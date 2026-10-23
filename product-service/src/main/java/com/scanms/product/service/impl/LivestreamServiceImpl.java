package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.LivestreamMapper;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.LivestreamService;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.dto.request.UpdateLivestreamRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.DateTimeException;
import java.util.Map;
import java.util.Collection;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LivestreamServiceImpl implements LivestreamService {
    private final LivestreamRepository repository;
    private final LivestreamMapper mapper;
    private final ProductAccess access;
    private final ProductRepository products;
    @Value("${clients.promotion.url:http://localhost:8085}") private String promotionUrl;

    @Override
    public LivestreamResponse create(CreateLivestreamRequest request) {
        access.requireApprovedStore(request.storeId());
        validateSchedule(request.scheduledAt(), request.participantRefs(), request.scheduledAt() != null);
        validateProducts(request.storeId(), request.productRefs());
        validateCollaborators(request.storeId(), request.collaboratorRefs());
        var livestream = mapper.toEntity(request);
        livestream.setParticipantRefs(participants(request.participantRefs(),null));
        livestream.setStatus(LivestreamStatus.SCHEDULED);
        livestream.setViewerCount(0L);
        livestream.setUniqueViewerCount(0L);
        livestream.setPeakConcurrentViewerCount(0L);
        livestream.setStartedAt(null);
        livestream.setEndedAt(null);
        return mapper.toResponse(repository.save(livestream));
    }

    @Override
    @Transactional(readOnly = true)
    public LivestreamResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LivestreamResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    public LivestreamResponse update(String id, UpdateLivestreamRequest request) {
        var livestream = owned(id);
        if (livestream.getStatus() == LivestreamStatus.ENDED || livestream.getStatus() == LivestreamStatus.CANCELLED)
            throw new AppException(ErrorCode.CONFLICT, "Livestream is closed");
        if (request.scheduledAt() != null || (request.participantRefs() != null && request.participantRefs().containsKey("endsAt"))) {
            if (request.scheduledAt() != null && livestream.getStatus() != LivestreamStatus.SCHEDULED)
                throw new AppException(ErrorCode.CONFLICT, "Không thể đổi lịch khi phiên đã bắt đầu.");
            var proposedParticipants = new java.util.LinkedHashMap<String, Object>(livestream.getParticipantRefs() == null ? Map.of() : livestream.getParticipantRefs());
            if (request.participantRefs() != null) proposedParticipants.putAll(request.participantRefs());
            validateSchedule(request.scheduledAt() == null ? livestream.getScheduledAt() : request.scheduledAt(), proposedParticipants, request.scheduledAt() != null);
        }
        if (request.title() != null) livestream.setTitle(request.title());
        if (request.description() != null) livestream.setDescription(request.description());
        if (request.providerSessionId() != null) livestream.setProviderSessionId(request.providerSessionId());
        if (request.playbackRef() != null) livestream.setPlaybackRef(request.playbackRef());
        if (request.scheduledAt() != null && livestream.getStatus() == LivestreamStatus.SCHEDULED)
            livestream.setScheduledAt(request.scheduledAt());
        if (request.productRefs() != null) {
            validateProducts(livestream.getStoreId(), request.productRefs());
            livestream.setProductRefs(request.productRefs());
        }
        boolean changedCollaborators=request.collaboratorRefs()!=null && !request.collaboratorRefs().equals(livestream.getCollaboratorRefs());
        if(changedCollaborators && livestream.getStatus()==LivestreamStatus.LIVE)
            throw new AppException(ErrorCode.CONFLICT,"Cannot replace collaborators after the livestream has started");
        if (request.participantRefs() != null || changedCollaborators)
            livestream.setParticipantRefs(participants(request.participantRefs()==null?livestream.getParticipantRefs():request.participantRefs(),changedCollaborators?null:livestream.getParticipantRefs()));
        if (request.collaboratorRefs() != null) {
            validateCollaborators(livestream.getStoreId(), request.collaboratorRefs());
            livestream.setCollaboratorRefs(request.collaboratorRefs());
        }
        return mapper.toResponse(repository.save(livestream));
    }

    public LivestreamResponse transition(String id, LivestreamStatus next) {
        var livestream = owned(id);
        if (next == LivestreamStatus.LIVE && livestream.getStatus() == LivestreamStatus.SCHEDULED) {
            validateCollaborators(livestream.getStoreId(),livestream.getCollaboratorRefs());
            if(livestream.getCollaboratorRefs()!=null && livestream.getCollaboratorRefs().get("collaboratorIds") instanceof Collection<?> ids && !ids.isEmpty()) {
                Object responses=livestream.getParticipantRefs()==null?null:livestream.getParticipantRefs().get("collaboratorResponses");
                if(!(responses instanceof Map<?,?> decisions) || ids.stream().anyMatch(candidate->!"ACCEPTED".equals(decisions.get(candidate.toString()))))
                    throw new AppException(ErrorCode.CONFLICT,"KOL được mời cần chấp nhận phiên livestream trước khi phát.");
            }
            livestream.setStatus(next);
            livestream.setStartedAt(LocalDateTime.now());
        } else if (next == LivestreamStatus.ENDED && livestream.getStatus() == LivestreamStatus.LIVE) {
            livestream.setStatus(next);
            livestream.setEndedAt(LocalDateTime.now());
        } else throw new AppException(ErrorCode.CONFLICT, "Invalid livestream state transition");
        return mapper.toResponse(repository.save(livestream));
    }

    public LivestreamResponse updateStats(String id, long viewers, long uniqueViewers, long concurrentViewers) {
        var livestream = owned(id);
        if (livestream.getStatus() != LivestreamStatus.LIVE || viewers < 0 || uniqueViewers < 0 || concurrentViewers < 0)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid livestream statistics");
        livestream.setViewerCount(viewers);
        livestream.setUniqueViewerCount(Math.max(livestream.getUniqueViewerCount(), uniqueViewers));
        livestream.setPeakConcurrentViewerCount(Math.max(livestream.getPeakConcurrentViewerCount(), concurrentViewers));
        return mapper.toResponse(repository.save(livestream));
    }

    @Override
    public LivestreamResponse respondInvite(String id, boolean accepted, org.springframework.security.oauth2.jwt.Jwt jwt) {
        var livestream = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
        if (livestream.getStatus() != LivestreamStatus.SCHEDULED)
            throw new AppException(ErrorCode.CONFLICT, "Livestream is closed");
        java.util.Map<String, Object> participants = new java.util.LinkedHashMap<>(
                livestream.getParticipantRefs() == null ? java.util.Map.of() : livestream.getParticipantRefs());
        String userId=access.userId(), collaboratorId=null;
        Object refs=livestream.getCollaboratorRefs()==null?null:livestream.getCollaboratorRefs().get("collaboratorIds");
        if(refs instanceof Collection<?> ids) for(Object candidate:ids) {
            try {
                if(userId.equals(collaborator(livestream.getStoreId(),candidate.toString()).get("userId"))) { collaboratorId=candidate.toString(); break; }
            } catch(AppException ex) { /* Another invited KOL is private to this caller. */ }
        }
        if(collaboratorId==null) throw new AppException(ErrorCode.FORBIDDEN,"Chỉ KOL được mời và đã chấp nhận hợp tác mới được phản hồi phiên live.");
        var responses=new java.util.LinkedHashMap<String,Object>();
        if(participants.get("collaboratorResponses") instanceof Map<?,?> previous) previous.forEach((key,value)->responses.put(key.toString(),value));
        responses.put(collaboratorId,accepted?"ACCEPTED":"REJECTED");
        participants.put("collaboratorResponses",responses);
        participants.put("inviteStatus",responses.containsValue("REJECTED")?"REJECTED":responses.size()==(refs instanceof Collection<?> ids?ids.size():0)&&responses.values().stream().allMatch("ACCEPTED"::equals)?"ACCEPTED":"PENDING");
        if (jwt != null) {
            participants.put("respondedBySubject", jwt.getSubject());
            participants.put("respondedAt", java.time.Instant.now().toString());
        }
        livestream.setParticipantRefs(participants);
        return mapper.toResponse(repository.save(livestream));
    }

    private com.scanms.product.entity.Livestream owned(String id) {
        var livestream = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
        access.requireApprovedStore(livestream.getStoreId());
        return livestream;
    }

    private Map<String,Object> participants(Map<String,Object> input,Map<String,Object> previous) {
        var result=new java.util.LinkedHashMap<String,Object>(input==null?Map.of():input);
        for(String key:List.of("inviteStatus","collaboratorResponses","respondedAt","respondedBySubject")) {
            result.remove(key);
            if(previous!=null && previous.containsKey(key)) result.put(key,previous.get(key));
        }
        return result;
    }

    private void validateSchedule(LocalDateTime start, Map<String, Object> participants, boolean checkStart) {
        // The existing API represents scheduledAt as UTC without an offset (FE sends ISO UTC).
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (checkStart && (start == null || !start.isAfter(now)))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giờ bắt đầu phải là thời điểm trong tương lai.");
        if (participants == null || !participants.containsKey("endsAt")) return;
        LocalDateTime end;
        try {
            if (!(participants.get("endsAt") instanceof String text) || text.isBlank()) throw new DateTimeException("Missing end time");
            try { end = OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
            catch (DateTimeException ex) { end = LocalDateTime.parse(text); }
        } catch (DateTimeException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giờ kết thúc không hợp lệ.");
        }
        if (!end.isAfter(now) || (start != null && !end.isAfter(start)))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giờ kết thúc phải sau giờ bắt đầu và chưa được trôi qua.");
    }

    private void validateProducts(String storeId, Map<String, Object> refs) {
        if (refs == null || refs.isEmpty()) return;
        if (!(refs.get("productIds") instanceof Collection<?> ids))
            throw new AppException(ErrorCode.INVALID_REQUEST, "productRefs.productIds must be a list");
        for (Object id : ids) {
            var product = products.findById(id.toString())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream product not found"));
            if (!storeId.equals(product.getStoreId()))
                throw new AppException(ErrorCode.FORBIDDEN, "Livestream product belongs to another store");
        }
    }

    private void validateCollaborators(String storeId, Map<String, Object> refs) {
        if (refs == null || refs.isEmpty()) return;
        if (!(refs.get("collaboratorIds") instanceof Collection<?> ids))
            throw new AppException(ErrorCode.INVALID_REQUEST, "collaboratorRefs.collaboratorIds must be a list");
        for(Object id:ids) collaborator(storeId,id.toString());
    }

    private Map<?,?> collaborator(String storeId,String id) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        var http = RestClient.builder().requestFactory(factory).build();
            try {
                Map<?, ?> response = http.get().uri(promotionUrl + "/api/v1/referrals/livestream-access?storeId={store}&collaboratorId={id}",storeId,id)
                        .headers(h -> h.setBearerAuth(access.bearerToken())).retrieve().body(Map.class);
                if (response == null || !(response.get("result") instanceof Map<?, ?> collaborator)
                        || !"APPROVED".equals(collaborator.get("approvalStatus")))
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Livestream collaborator must be approved");
                return collaborator;
            } catch (RestClientException ex) {
                throw new AppException(ErrorCode.CONFLICT, "Cannot verify livestream collaborator");
            }
    }
}
