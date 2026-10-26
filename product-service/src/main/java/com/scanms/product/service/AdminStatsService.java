package com.scanms.product.service;

import com.scanms.product.dto.response.AdminStatsResponse;

public interface AdminStatsService {
    AdminStatsResponse storeStats();

    AdminStatsResponse productStats();
}
