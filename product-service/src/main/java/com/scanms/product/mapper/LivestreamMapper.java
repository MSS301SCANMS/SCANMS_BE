package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.entity.Livestream;
import org.springframework.stereotype.Component;

@Component
public class LivestreamMapper {
    public Livestream toEntity(CreateLivestreamRequest request) {
        return Livestream.builder()
                .storeId(request.storeId())
                .title(request.title())
                .description(request.description())
                .status(request.status())
                .providerSessionId(request.providerSessionId())
                .playbackRef(request.playbackRef())
                .productRefs(request.productRefs())
                .participantRefs(request.participantRefs())
                .collaboratorRefs(request.collaboratorRefs())
                .viewerCount(request.viewerCount())
                .uniqueViewerCount(request.uniqueViewerCount())
                .peakConcurrentViewerCount(request.peakConcurrentViewerCount())
                .scheduledAt(request.scheduledAt())
                .startedAt(request.startedAt())
                .endedAt(request.endedAt())
                .build();
    }

    public LivestreamResponse toResponse(Livestream entity) {
        return new LivestreamResponse(
                entity.getLivestreamId(),
                entity.getStoreId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getProviderSessionId(),
                entity.getPlaybackRef(),
                entity.getProductRefs(),
                entity.getParticipantRefs(),
                entity.getCollaboratorRefs(),
                entity.getViewerCount(),
                entity.getUniqueViewerCount(),
                entity.getPeakConcurrentViewerCount(),
                entity.getScheduledAt(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
