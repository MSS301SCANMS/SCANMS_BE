package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.PublicVoucherService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PublicVoucherController {
    private final PublicVoucherService service;

    @GetMapping("/api/v1/vouchers/public/platform")
    public ApiResponse<List<Map<String, Object>>> platform(@RequestParam(required = false) String storeId) {
        return ApiResponse.success(service.platform(storeId));
    }

    @GetMapping("/api/v1/vouchers/public")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam String storeId) {
        return ApiResponse.success(service.listByStore(storeId));
    }
}
