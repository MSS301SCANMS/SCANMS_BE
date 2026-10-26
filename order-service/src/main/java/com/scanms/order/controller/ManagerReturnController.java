package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.service.ManagerStatisticsAccess;
import com.scanms.order.service.ShopReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/returns/manager")
public class ManagerReturnController {
    private final ManagerStatisticsAccess access;
    private final ShopReturnService service;

    @GetMapping
    public ApiResponse<List<ShopReturnService.View>> list(@AuthenticationPrincipal Jwt jwt) {
        access.requireManager(jwt);
        return ApiResponse.success(service.managerList());
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<Map<String, Object>> decide(
            @PathVariable String id,
            @Valid @RequestBody ShopReturnService.ManagerDecision input,
            @AuthenticationPrincipal Jwt jwt) {
        access.requireManager(jwt);
        return ApiResponse.success(service.managerDecide(id, input));
    }
}
