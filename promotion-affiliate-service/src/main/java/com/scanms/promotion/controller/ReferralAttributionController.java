package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.ReferralAttributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReferralAttributionController {
    private final ReferralAttributionService service;

    @GetMapping("/api/v1/referrals/attribution/{id}")
    public ApiResponse<Map<String, String>> verify(
            @PathVariable String id, @RequestParam String productId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.verify(id, productId, jwt));
    }

    @GetMapping("/api/v1/referrals/livestream-attribution/{id}")
    public ApiResponse<Map<String, String>> livestream(
            @PathVariable String id, @RequestParam String productId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.livestream(id, productId, jwt));
    }
}
