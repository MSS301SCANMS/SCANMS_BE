package com.scanms.product.dto.response;

import com.scanms.product.constant.LivestreamStatus;
import java.time.LocalDateTime;
import java.util.Map;

public record LivestreamResponse(
        String livestreamId,
        String storeId,
        String title,
        String description,
        LivestreamStatus status,
        String providerSessionId,
        String playbackRef,
        Map<String, Object> productRefs,
        Map<String, Object> participantRefs,
        Map<String, Object> collaboratorRefs,
        Long viewerCount,
        Long uniqueViewerCount,
        Long peakConcurrentViewerCount,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
