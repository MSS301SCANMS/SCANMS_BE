package com.scanms.product.dto.request;

import com.scanms.product.constant.LivestreamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import jakarta.validation.constraints.PositiveOrZero;

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
        @PositiveOrZero Long viewerCount,
        @PositiveOrZero Long uniqueViewerCount,
        @PositiveOrZero Long peakConcurrentViewerCount,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {}
