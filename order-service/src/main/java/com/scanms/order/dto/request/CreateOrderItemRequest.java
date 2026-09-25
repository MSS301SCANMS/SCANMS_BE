package com.scanms.order.dto.request;

import com.scanms.order.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateOrderItemRequest(
        @NotNull String sellerOrderId,
        @NotNull String productId,
        String variantId,
        String sourceLivestreamId,
        @Positive Integer quantity,
        Map<String, Object> productSnapshot,
        String selectedSize,
        @PositiveOrZero Long unitPrice,
        @PositiveOrZero Long grossAmountVnd,
        @PositiveOrZero Long discountAmountVnd,
        @PositiveOrZero Long netPaidAmountVnd,
        Map<String, Object> discountAllocations,
        String referralLinkId,
        Map<String, Object> ruleSnapshot
) {}
