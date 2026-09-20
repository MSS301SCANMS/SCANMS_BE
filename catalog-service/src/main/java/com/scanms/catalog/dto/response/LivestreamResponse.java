package com.scanms.catalog.dto.response;

import com.scanms.catalog.constant.LivestreamStatus;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record LivestreamResponse(
        UUID livestreamId,
        UUID storeId,
        String title,
        LivestreamStatus status,
        String providerSessionId,
        String playbackRef,
        Map<String, Object> productRefs,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
