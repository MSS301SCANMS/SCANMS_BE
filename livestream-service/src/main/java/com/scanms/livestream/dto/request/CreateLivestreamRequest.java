package com.scanms.livestream.dto.request;

import com.scanms.livestream.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateLivestreamRequest(
        @NotNull UUID storeId,
        @NotBlank String title,
        LivestreamStatus status,
        String providerSessionId,
        String playbackRef,
        Map<String, Object> productRefs,
        LocalDateTime scheduledAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {}
