package com.scanms.livestream.dto.response;

import com.scanms.livestream.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

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
