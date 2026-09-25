package com.scanms.product.dto.request;

import com.scanms.product.constant.LivestreamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

public record CreateLivestreamRequest(
        @NotNull String storeId,
        @NotBlank String title,
        String description,
        LivestreamStatus status,
        String providerSessionId,
        String playbackRef,
        Map<String, Object> productRefs,
        Map<String, Object> participantRefs,
        Map<String, Object> collaboratorRefs,
        @jakarta.validation.constraints.PositiveOrZero Long viewerCount,
        @jakarta.validation.constraints.PositiveOrZero Long uniqueViewerCount,
        @jakarta.validation.constraints.PositiveOrZero Long peakConcurrentViewerCount,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {}
