package com.scanms.order.dto.response;

/** Paid-order metrics for one livestream. GMV is before discounts; net sales is after discounts. */
public record LivestreamSalesResponse(String livestreamId, String storeId, long buyerCount, long orderCount,
        long soldQuantity, long gmvVnd, long netSalesVnd) {}
