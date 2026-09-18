package com.scanms.order.dto.request;

import com.scanms.order.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String idempotencyKey,
        @PositiveOrZero Long payableVnd,
        @NotBlank String currency,
        Map<String, Object> shippingSnapshot,
        OrderStatus status,
        Map<String, Object> sagaState,
        LocalDateTime expiresAt
) {}
