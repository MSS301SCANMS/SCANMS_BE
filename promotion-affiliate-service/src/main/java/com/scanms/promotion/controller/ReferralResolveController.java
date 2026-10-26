package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.ReferralResolveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReferralResolveController {
    private final ReferralResolveService service;

    @GetMapping("/api/v1/referrals/resolve/{token}")
    public ApiResponse<Map<String, String>> resolve(@PathVariable String token) {
        return ApiResponse.success(service.resolve(token));
    }
}
