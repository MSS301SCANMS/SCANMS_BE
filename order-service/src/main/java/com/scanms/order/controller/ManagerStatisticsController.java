package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.response.ManagerStatisticsResponse;
import com.scanms.order.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/orders/statistics")
public class ManagerStatisticsController {
    private final ManagerStatisticsService service;
    private final ManagerStatisticsAccess access;
    @GetMapping
    public ApiResponse<ManagerStatisticsResponse> summary(@RequestParam(defaultValue = "month") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
            @AuthenticationPrincipal Jwt jwt) {
        access.requireManager(jwt);
        return ApiResponse.success(service.summary(range, anchor));
    }
}
