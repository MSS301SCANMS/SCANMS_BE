package com.scanms.order.dto.response;

import com.scanms.order.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record OrderItemResponse(
        String orderItemId,
        String sellerOrderId,
        String productId,
        String variantId,
        String sourceLivestreamId,
        Integer quantity,
        Map<String, Object> productSnapshot,
        String selectedSize,
        Long unitPrice,
        Long grossAmountVnd,
        Long discountAmountVnd,
        Long netPaidAmountVnd,
        Map<String, Object> discountAllocations,
        String referralLinkId,
        Map<String, Object> ruleSnapshot,
        LocalDateTime createdAt
) {}
