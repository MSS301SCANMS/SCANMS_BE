package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.response.StorePresenceResponse;
import com.scanms.product.service.StorePresenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StorePresenceController {
    private final StorePresenceService service;

    @PostMapping("/my-store/presence")
    public ApiResponse<List<StorePresenceResponse>> heartbeat() {
        return ApiResponse.success(service.heartbeat());
    }

    @GetMapping("/public/id/{storeId}/presence")
    public ApiResponse<StorePresenceResponse> presence(@PathVariable String storeId) {
        return ApiResponse.success(service.publicPresence(storeId));
    }
}
