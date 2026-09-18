package com.scanms.order.dto.response;

import com.scanms.order.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record OrderResponse(
        UUID orderId,
        UUID customerId,
        String idempotencyKey,
        Long payableVnd,
        String currency,
        Map<String, Object> shippingSnapshot,
        OrderStatus status,
        Map<String, Object> sagaState,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
