package com.scanms.product.service.impl;

import com.scanms.product.client.LivestreamAffiliateClient;
import com.scanms.product.constant.*;
import com.scanms.product.dto.request.*;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.entity.Livestream;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.LivestreamMapper;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class LivestreamServiceImpl implements LivestreamService {
    private static final Set<String> CONSENT_KEYS = Set.of("inviteStatus", "collaboratorResponses", "respondedBySubject", "respondedAt", "voucherDrops", "lifecycle", "broadcast");
    private final LivestreamRepository repository;
    private final LivestreamMapper mapper;
    private final ProductAccess access;
    private final ProductRepository products;
    private final LivestreamAffiliateClient affiliate;
    private final LivestreamSchedulePolicy schedule;
    private final LivestreamLifecycleService lifecycle;
    private final LivestreamBroadcastPolicy broadcast;

    public LivestreamResponse create(CreateLivestreamRequest request) {
        access.requireApprovedStore(request.storeId());
        validateProducts(request.storeId(), request.productRefs());
        var ids = collaborators(request.collaboratorRefs());
        for (String id : ids) partnership(request.storeId(), id);
        var participants = metadata(request.participantRefs());
        validateSchedule(request.scheduledAt(), participants, true);
        setResponses(participants, ids, Map.of());
        var live = mapper.toEntity(request);
        live.setProductRefs(request.productRefs() == null ? null : Map.of("productIds", request.productRefs().getOrDefault("productIds", List.of())));
        live.setParticipantRefs(participants);
        live.setStatus(LivestreamStatus.SCHEDULED);
        live.setViewerCount(0L);
        live.setUniqueViewerCount(0L);
        live.setPeakConcurrentViewerCount(0L);
        live.setStartedAt(null);
        live.setEndedAt(null);
        return mapper.toResponse(repository.save(live));
    }

    public LivestreamResponse getById(String id) {
        return mapper.toResponse(locked(id));
    }

    public List<LivestreamResponse> findAll() {
        lifecycle.closeElapsedSessions();
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    public LivestreamResponse update(String id, UpdateLivestreamRequest request) {
        var live = owned(id);
        if (live.getStatus() == LivestreamStatus.ENDED || live.getStatus() == LivestreamStatus.CANCELLED)
            throw conflict("Livestream is closed");
        var oldIds = collaborators(live.getCollaboratorRefs());
        var ids = request.collaboratorRefs() == null ? oldIds : collaborators(request.collaboratorRefs());
        if (live.getStatus() == LivestreamStatus.LIVE && (!ids.equals(oldIds) || request.scheduledAt() != null))
            throw conflict("Cannot change participants or schedule after livestream starts");
        if (request.collaboratorRefs() != null) for (String kol : ids) partnership(live.getStoreId(), kol);
        var participants = new LinkedHashMap<String, Object>(live.getParticipantRefs() == null ? Map.of() : live.getParticipantRefs());
        if (request.participantRefs() != null) participants.putAll(metadata(request.participantRefs()));
        if (request.scheduledAt() != null && live.getStatus() != LivestreamStatus.SCHEDULED)
            throw conflict("Cannot reschedule a live session");
        var schedule = request.scheduledAt() == null ? live.getScheduledAt() : request.scheduledAt();
        validateSchedule(schedule, participants, request.scheduledAt() != null);
        setResponses(participants, ids, responses(live));
        if (request.title() != null) {
            if (request.title().isBlank()) throw invalid("Title is required");
            live.setTitle(request.title());
        }
        if (request.description() != null) live.setDescription(request.description());
        if (request.providerSessionId() != null) live.setProviderSessionId(request.providerSessionId());
        if (request.playbackRef() != null) live.setPlaybackRef(request.playbackRef());
        if (request.productRefs() != null) {
            validateProducts(live.getStoreId(), request.productRefs());
            var refs = new LinkedHashMap<String, Object>(live.getProductRefs() == null ? Map.of() : live.getProductRefs());
            refs.put("productIds", request.productRefs().getOrDefault("productIds", List.of()));
            live.setProductRefs(refs);
        }
        live.setParticipantRefs(participants);
        live.setScheduledAt(schedule);
        if (request.collaboratorRefs() != null) live.setCollaboratorRefs(request.collaboratorRefs());
        return mapper.toResponse(repository.save(live));
    }

    public LivestreamResponse transition(String id, LivestreamStatus next) {
        var live = owned(id);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (next == LivestreamStatus.LIVE && live.getStatus() == LivestreamStatus.SCHEDULED) {
            if (live.getScheduledAt() != null && live.getScheduledAt().isAfter(now))
                throw conflict("The scheduled start time has not arrived");
            var accepted = responses(live);
            for (String kol : collaborators(live.getCollaboratorRefs())) {
                if (!"ACCEPTED".equals(accepted.get(kol)))
                    throw conflict("Every invited KOL must accept before starting");
                partnership(live.getStoreId(), kol);
            }
            validateProducts(live.getStoreId(), live.getProductRefs());
            var refs = live.getParticipantRefs();
            if (refs != null && refs.get("endsAt") != null && !parseEnd(refs.get("endsAt")).isAfter(now))
                throw conflict("Livestream end time has passed");
            live.setStatus(next);
            live.setStartedAt(now);
            broadcast.start(live, now.toInstant(ZoneOffset.UTC));
        } else if (next == LivestreamStatus.ENDED && live.getStatus() == LivestreamStatus.LIVE) {
            live.setStatus(next);
            live.setEndedAt(now);
        } else throw conflict("Invalid livestream state transition");
        return mapper.toResponse(repository.save(live));
    }

    public LivestreamResponse updateStats(String id, long viewers, long uniqueViewers, long concurrentViewers) {
        var live = locked(id);
        requireHost(live);
        if (!Set.of(LivestreamStatus.LIVE, LivestreamStatus.ENDED).contains(live.getStatus()) || viewers < 0 || uniqueViewers < 0 || concurrentViewers < 0)
            throw invalid("Invalid livestream statistics");
        live.setViewerCount(Math.max(value(live.getViewerCount()), viewers));
        live.setUniqueViewerCount(Math.max(value(live.getUniqueViewerCount()), uniqueViewers));
        live.setPeakConcurrentViewerCount(Math.max(value(live.getPeakConcurrentViewerCount()), concurrentViewers));
        return mapper.toResponse(repository.save(live));
    }

    public LivestreamResponse respondInvite(String id, boolean accepted, Jwt jwt) {
        var live = locked(id);
        if (live.getStatus() != LivestreamStatus.SCHEDULED)
            throw conflict("Invitations can only be changed before the live starts");
        String caller = access.userId(), matched = null;
        for (String kol : collaborators(live.getCollaboratorRefs())) {
            try {
                if (caller.equals(partnership(live.getStoreId(), kol).get("userId"))) {
                    matched = kol;
                    break;
                }
            } catch (AppException ex) {
                if (ex.getErrorCode() != ErrorCode.FORBIDDEN) throw ex;
            }
        }
        if (matched == null) throw new AppException(ErrorCode.FORBIDDEN, "Only the invited KOL may respond");
        var participants = new LinkedHashMap<String, Object>(live.getParticipantRefs() == null ? Map.of() : live.getParticipantRefs());
        var decisions = new LinkedHashMap<String, Object>(responses(live));
        decisions.put(matched, accepted ? "ACCEPTED" : "REJECTED");
        setResponses(participants, collaborators(live.getCollaboratorRefs()), decisions);
        if (jwt != null) participants.put("respondedBySubject", jwt.getSubject());
        participants.put("respondedAt", Instant.now().toString());
        live.setParticipantRefs(participants);
        return mapper.toResponse(repository.save(live));
    }

    private Livestream owned(String id) {
        var live = locked(id);
        access.requireApprovedStore(live.getStoreId());
        return live;
    }

    public LivestreamResponse pinProduct(String id, String productId) {
        var live = locked(id);
        requireHost(live);
        if (live.getStatus() != LivestreamStatus.LIVE) throw conflict("Phiên chưa phát sóng.");
        if (productId != null) {
            if (live.getProductRefs() == null || !(live.getProductRefs().get("productIds") instanceof Collection<?> ids) || !ids.contains(productId))
                throw invalid("Sản phẩm không thuộc phiên live.");
            validateProducts(live.getStoreId(), Map.of("productIds", List.of(productId)));
        }
        var refs = new LinkedHashMap<String, Object>(live.getProductRefs() == null ? Map.of() : live.getProductRefs());
        refs.put("pinnedProductId", productId);
        live.setProductRefs(refs);
        return mapper.toResponse(repository.save(live));
    }

    private Livestream locked(String id) {
        var live = repository.lockById(id).orElseThrow(() -> missing(id));
        schedule.closeIfElapsed(live, Instant.now());
        broadcast.closeIfDisconnected(live, Instant.now());
        return live;
    }

    private void requireHost(Livestream live) {
        // Store ownership is sufficient; an affiliate outage must not block the shop's controls.
        try {
            access.requireApprovedStore(live.getStoreId());
            return;
        } catch (AppException ex) {
            if (ex.getErrorCode() != ErrorCode.FORBIDDEN) throw ex;
        }
        String user = access.userId();
        for (String kol : collaborators(live.getCollaboratorRefs())) {
            if (!"ACCEPTED".equals(responses(live).get(kol))) continue;
            try {
                if (user.equals(partnership(live.getStoreId(), kol).get("userId"))) return;
            } catch (AppException ex) {
                if (ex.getErrorCode() != ErrorCode.FORBIDDEN) throw ex;
            }
        }
        throw new AppException(ErrorCode.FORBIDDEN, "Only the store owner or an accepted KOL may control this livestream");
    }

    private Map<String, Object> partnership(String store, String kol) {
        try {
            var response = affiliate.verify("Bearer " + access.bearerToken(), store, kol);
            Map<?, ?> result = response != null && response.get("result") instanceof Map<?, ?> value ? value : null;
            if (result == null || !"APPROVED".equals(result.get("approvalStatus")) || result.get("userId") == null)
                throw conflict("Approved shop partnership required");
            var verified = new LinkedHashMap<String, Object>();
            result.forEach((key, value) -> verified.put(key.toString(), value));
            return verified;
        } catch (FeignException ex) {
            if (ex.status() == 403 || ex.status() == 404)
                throw new AppException(ErrorCode.FORBIDDEN, "Cannot access this KOL partnership");
            throw conflict("KOL must be approved and have accepted the shop partnership");
        }
    }

    private List<String> collaborators(Map<String, Object> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        if (!(refs.get("collaboratorIds") instanceof Collection<?> ids) || ids.size() > 20)
            throw invalid("collaboratorIds must contain at most 20 ids");
        var result = new ArrayList<String>();
        for (Object id : ids) {
            if (!(id instanceof String s) || !s.matches("[A-Za-z0-9_-]{1,100}") || result.contains(s))
                throw invalid("Invalid or duplicate KOL reference");
            result.add(s);
        }
        return result;
    }

    private void validateProducts(String store, Map<String, Object> refs) {
        if (refs == null || refs.isEmpty()) return;
        if (!(refs.get("productIds") instanceof Collection<?> ids) || ids.size() > 100)
            throw invalid("Invalid product references");
        for (Object id : ids) {
            var product = products.findById(Objects.toString(id, "")).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
            if (!store.equals(product.getStoreId()))
                throw new AppException(ErrorCode.FORBIDDEN, "Product belongs to another store");
            if (product.getStatus() != ProductStatus.ACTIVE) throw conflict("Livestream products must be active");
        }
    }

    private Map<String, Object> metadata(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>(input == null ? Map.of() : input);
        CONSENT_KEYS.forEach(result::remove);
        return result;
    }

    private Map<String, Object> responses(Livestream live) {
        var result = new LinkedHashMap<String, Object>();
        if (live.getParticipantRefs() != null && live.getParticipantRefs().get("collaboratorResponses") instanceof Map<?, ?> values)
            values.forEach((key, value) -> result.put(key.toString(), value));
        return result;
    }

    private void setResponses(Map<String, Object> participants, List<String> ids, Map<String, Object> old) {
        var result = new LinkedHashMap<String, Object>();
        for (String id : ids) result.put(id, old.getOrDefault(id, "PENDING"));
        participants.put("collaboratorResponses", result);
        participants.put("inviteStatus", result.containsValue("REJECTED") ? "REJECTED" : result.containsValue("PENDING") ? "PENDING" : ids.isEmpty() ? "NOT_REQUIRED" : "ACCEPTED");
    }

    private void validateSchedule(LocalDateTime schedule, Map<String, Object> participants, boolean changed) {
        if (changed && schedule != null && !schedule.isAfter(LocalDateTime.now(ZoneOffset.UTC)))
            throw invalid("Scheduled time must be in the future");
        if (participants.get("endsAt") != null) {
            var end = parseEnd(participants.get("endsAt"));
            if (!end.isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw invalid("End time must be in the future");
            if (!end.isAfter(schedule == null ? LocalDateTime.now(ZoneOffset.UTC) : schedule))
                throw invalid("End time must be after start time");
        }
    }

    private LocalDateTime parseEnd(Object value) {
        try {
            return OffsetDateTime.parse(value.toString()).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (java.time.format.DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value.toString());
            } catch (Exception ignored) {
                throw invalid("Invalid livestream end time");
            }
        }
    }

    private long value(Long number) {
        return number == null ? 0 : number;
    }

    private AppException missing(String id) {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id);
    }

    private AppException invalid(String message) {
        return new AppException(ErrorCode.INVALID_REQUEST, message);
    }

    private AppException conflict(String message) {
        return new AppException(ErrorCode.CONFLICT, message);
    }
}
