package com.scanms.user.controller;

import com.scanms.user.dto.ApiResponse;
import com.scanms.user.dto.response.AdminStatsResponse;
import com.scanms.user.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thống kê người dùng cho dashboard admin. */
@RestController
@RequiredArgsConstructor
public class AdminStatsController {
    private final AdminStatsService stats;

    @GetMapping("/api/v1/users/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminStatsResponse> userStats() {
        return ApiResponse.success(stats.userStats());
    }
}
