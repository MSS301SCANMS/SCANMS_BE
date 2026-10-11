package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.response.StoreSalesResponse;
import com.scanms.order.service.LivestreamSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-sales")
@RequiredArgsConstructor
public class StoreSalesController {
    private final LivestreamSalesService service;

    @GetMapping("/{storeId}")
    public ApiResponse<StoreSalesResponse> get(@PathVariable String storeId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.storeSummary(storeId, jwt));
    }
}
