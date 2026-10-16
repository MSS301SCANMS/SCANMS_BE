package com.scanms.order.dto.response;

public record StoreSalesResponse(String storeId, long orderCount, long paidOrderCount, long paidRevenueVnd) {}
