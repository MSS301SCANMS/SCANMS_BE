package com.scanms.order.dto.response;

import com.scanms.order.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ReturnRequestResponse(
        UUID returnRequestId,
        UUID orderItemId,
        Integer quantity,
        String reason,
        Map<String, Object> evidenceRefs,
        ReturnRequestStatus status,
        String decisionReason,
        Long refundAmount,
        String refundReference,
        LocalDateTime requestedAt,
        LocalDateTime resolvedAt
) {}
