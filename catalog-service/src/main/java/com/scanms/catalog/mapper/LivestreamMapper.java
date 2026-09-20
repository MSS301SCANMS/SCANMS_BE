package com.scanms.catalog.mapper;

import com.scanms.catalog.dto.request.CreateLivestreamRequest;
import com.scanms.catalog.dto.response.LivestreamResponse;
import com.scanms.catalog.entity.Livestream;
import org.springframework.stereotype.Component;

@Component
public class LivestreamMapper {
    public Livestream toEntity(CreateLivestreamRequest request) {
        return Livestream.builder()
                .storeId(request.storeId())
                .title(request.title())
                .status(request.status())
                .providerSessionId(request.providerSessionId())
                .playbackRef(request.playbackRef())
                .productRefs(request.productRefs())
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
                entity.getStatus(),
                entity.getProviderSessionId(),
                entity.getPlaybackRef(),
                entity.getProductRefs(),
                entity.getScheduledAt(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
