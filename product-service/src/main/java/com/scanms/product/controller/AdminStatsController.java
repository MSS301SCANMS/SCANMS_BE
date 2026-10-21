package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.response.AdminStatsResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.service.AdminStatsService;
import com.scanms.product.service.ProductAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thống kê gian hàng & sản phẩm cho dashboard admin (ADMIN/MANAGER). */
@RestController
@RequiredArgsConstructor
public class AdminStatsController {
    private final AdminStatsService stats;
    private final ProductAccess access;

    @GetMapping("/api/v1/stores/admin/stats")
    public ApiResponse<AdminStatsResponse> storeStats() {
        requireAdmin();
        return ApiResponse.success(stats.storeStats());
    }

    @GetMapping("/api/v1/products/admin/stats")
    public ApiResponse<AdminStatsResponse> productStats() {
        requireAdmin();
        return ApiResponse.success(stats.productStats());
    }

    private void requireAdmin() {
        if (!access.isManager()) throw new AppException(ErrorCode.FORBIDDEN, "Admin or manager role required");
    }
}
