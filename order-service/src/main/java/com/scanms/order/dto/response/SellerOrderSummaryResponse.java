package com.scanms.order.dto.response;

import com.scanms.order.constant.SellerOrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SellerOrderSummaryResponse(
        String sellerOrderId,
        String orderId,
        String storeId,
        SellerOrderStatus status,
        Map<String, Object> totalsSnapshot,
        String shipmentId,
        LocalDateTime deliveredAt,
        LocalDateTime returnDeadline,
        List<OrderItemResponse> items
) {}
