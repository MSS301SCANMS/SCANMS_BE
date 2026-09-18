package com.scanms.order.dto.response;

import com.scanms.order.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record OrderItemResponse(
        UUID orderItemId,
        UUID sellerOrderId,
        UUID productId,
        Integer quantity,
        Map<String, Object> productSnapshot,
        String selectedSize,
        Long unitPrice,
        Map<String, Object> discountAllocations,
        UUID referralLinkId,
        Map<String, Object> ruleSnapshot,
        LocalDateTime createdAt
) {}
