package com.scanms.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record CheckoutRequest(
        String customerId,
        String idempotencyKey,
        String customerName,
        String customerPhone,
        String customerEmail,
        String shippingAddress,
        String paymentMethod,
        String currency,
        String voucherCode,
        Long voucherDiscountVnd,
        String voucherFundingSource,
        String note,
        Map<String, Object> shippingSnapshot,
        @NotEmpty List<@Valid CheckoutItemRequest> items
) {}
