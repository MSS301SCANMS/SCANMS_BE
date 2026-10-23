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
import com.scanms.product.entity.Livestream;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.LinkedHashMap;

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
        validateProducts(request.storeId(), request.productRefs());
        validateCollaborators(request.collaboratorRefs());
        var livestream = mapper.toEntity(request);
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
        if (request.participantRefs() != null) livestream.setParticipantRefs(request.participantRefs());
        if (request.collaboratorRefs() != null) {
            validateCollaborators(request.collaboratorRefs());
            livestream.setCollaboratorRefs(request.collaboratorRefs());
        }
        return mapper.toResponse(repository.save(livestream));
    }

    public LivestreamResponse transition(String id, LivestreamStatus next) {
        var livestream = owned(id);
        if (next == LivestreamStatus.LIVE && livestream.getStatus() == LivestreamStatus.SCHEDULED) {
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
    public LivestreamResponse respondInvite(String id, boolean accepted, Jwt jwt) {
        var livestream = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
        if (livestream.getStatus() == LivestreamStatus.ENDED || livestream.getStatus() == LivestreamStatus.CANCELLED)
            throw new AppException(ErrorCode.CONFLICT, "Livestream is closed");
        Map<String, Object> participants = new LinkedHashMap<>(
                livestream.getParticipantRefs() == null ? Map.of() : livestream.getParticipantRefs());
        participants.put("inviteStatus", accepted ? "ACCEPTED" : "REJECTED");
        if (jwt != null) {
            participants.put("respondedBySubject", jwt.getSubject());
            participants.put("respondedAt", Instant.now().toString());
        }
        livestream.setParticipantRefs(participants);
        return mapper.toResponse(repository.save(livestream));
    }

    private Livestream owned(String id) {
        var livestream = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
        access.requireApprovedStore(livestream.getStoreId());
        return livestream;
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

    private void validateCollaborators(Map<String, Object> refs) {
        if (refs == null || refs.isEmpty()) return;
        if (!(refs.get("collaboratorIds") instanceof Collection<?> ids))
            throw new AppException(ErrorCode.INVALID_REQUEST, "collaboratorRefs.collaboratorIds must be a list");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        var http = RestClient.builder().requestFactory(factory).build();
        for (Object id : ids) {
            try {
                Map<?, ?> response = http.get().uri(promotionUrl + "/api/v1/collaborators/" + id)
                        .headers(h -> h.setBearerAuth(access.bearerToken())).retrieve().body(Map.class);
                if (response == null || !(response.get("result") instanceof Map<?, ?> collaborator)
                        || !"APPROVED".equals(collaborator.get("approvalStatus")))
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Livestream collaborator must be approved");
            } catch (RestClientException ex) {
                throw new AppException(ErrorCode.CONFLICT, "Cannot verify livestream collaborator");
            }
        }
    }
}
