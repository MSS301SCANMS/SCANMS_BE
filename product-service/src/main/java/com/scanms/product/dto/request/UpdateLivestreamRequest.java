package com.scanms.product.dto.request;

import com.scanms.product.constant.LivestreamStatus;
import java.time.LocalDateTime;
import java.util.Map;

public record UpdateLivestreamRequest(
        String title,
        String description,
        LivestreamStatus status,
        String providerSessionId,
        String playbackRef,
        Map<String, Object> productRefs,
        Map<String, Object> participantRefs,
        Map<String, Object> collaboratorRefs,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {}
