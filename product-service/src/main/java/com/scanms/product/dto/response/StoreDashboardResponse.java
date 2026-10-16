package com.scanms.product.dto.response;

import java.math.BigDecimal;

public record StoreDashboardResponse(String storeId, BigDecimal ratingAverage, long ratingCount,
        long productCount, long livestreamCount, long orderCount, long paidRevenueVnd) {}
