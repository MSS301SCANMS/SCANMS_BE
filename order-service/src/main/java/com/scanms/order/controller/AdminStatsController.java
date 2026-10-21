package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.response.AdminStatsResponse;
import com.scanms.order.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thống kê đơn hàng cho dashboard admin. */
@RestController
@RequiredArgsConstructor
public class AdminStatsController {
    private final AdminStatsService stats;

    @GetMapping("/api/v1/orders/admin/stats")
    @PreAuthorize("@financeBoundary.canViewStats(authentication)")
    public ApiResponse<AdminStatsResponse> orderStats() {
        return ApiResponse.success(stats.orderStats());
    }
}
