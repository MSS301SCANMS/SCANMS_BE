package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.LivestreamPartnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Private verification for Product without exposing KYC or banking details. */
@RestController
@RequiredArgsConstructor
public class LivestreamPartnershipController {
    private final LivestreamPartnershipService service;

    @GetMapping("/api/v1/referrals/livestream-access")
    public ApiResponse<Map<String, Object>> verify(@RequestParam String storeId, @RequestParam String collaboratorId) {
        return ApiResponse.success(service.verify(storeId, collaboratorId));
    }
}
