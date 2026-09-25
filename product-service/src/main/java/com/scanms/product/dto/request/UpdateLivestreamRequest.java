package com.scanms.product.dto.request;

import com.scanms.product.constant.LivestreamStatus;
import java.time.LocalDateTime;
import java.util.Map;

public record UpdateLivestreamRequest(
        String title,
        LivestreamStatus status,
        String providerSessionId,
        String playbackRef,
        Map<String, Object> productRefs,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {}
