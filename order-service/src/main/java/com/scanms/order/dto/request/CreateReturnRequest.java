package com.scanms.order.dto.request;

import com.scanms.order.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateReturnRequest(
        @NotNull String orderItemId,
        @Positive Integer quantity,
        @NotBlank String reason,
        Map<String, Object> evidenceRefs,
        ReturnRequestStatus status,
        String decisionReason,
        Long refundAmount,
        String refundReference,
        LocalDateTime requestedAt,
        LocalDateTime resolvedAt
) {}
