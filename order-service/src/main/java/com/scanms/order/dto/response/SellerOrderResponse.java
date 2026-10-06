package com.scanms.order.dto.response;

import com.scanms.order.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record SellerOrderResponse(
        String sellerOrderId,
        String orderId,
        String storeId,
        Map<String, Object> totalsSnapshot,
        SellerOrderStatus status,
        LocalDateTime deliveredAt,
        LocalDateTime returnDeadline,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
