package com.scanms.order.dto.response;

import com.scanms.order.constant.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CheckoutResponse(
        String orderId,
        String publicOrderCode,
        String customerId,
        String idempotencyKey,
        Long payableVnd,
        Long finalAmount,
        Long totalGrossVnd,
        Long totalDiscountVnd,
        String currency,
        OrderStatus status,
        Map<String, Object> shippingSnapshot,
        LocalDateTime createdAt,
        List<SellerOrderSummaryResponse> sellerOrders
) {}
