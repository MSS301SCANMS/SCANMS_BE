package com.scanms.order.dto.request;

import com.scanms.order.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateOrderItemRequest(
        @NotNull UUID sellerOrderId,
        @NotNull UUID productId,
        @Positive Integer quantity,
        Map<String, Object> productSnapshot,
        String selectedSize,
        @PositiveOrZero Long unitPrice,
        Map<String, Object> discountAllocations,
        UUID referralLinkId,
        Map<String, Object> ruleSnapshot
) {}
