package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.response.LivestreamSalesResponse;
import com.scanms.order.service.LivestreamSalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/livestream-sales")
@RequiredArgsConstructor
public class LivestreamSalesController {
    private final LivestreamSalesService service;

    @GetMapping("/{livestreamId}")
    public ApiResponse<LivestreamSalesResponse> get(@PathVariable String livestreamId,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.summary(livestreamId, jwt));
    }
}
