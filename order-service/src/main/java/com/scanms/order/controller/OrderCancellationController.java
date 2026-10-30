package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.OrderCancellationDecision;
import com.scanms.order.dto.request.OrderCancellationRequest;
import com.scanms.order.service.OrderCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderCancellationController {
    private final OrderCancellationService service;

    @PostMapping("/orders/{id}/cancellation")
    public ApiResponse<Map<String, Object>> request(@PathVariable String id, @Valid @RequestBody OrderCancellationRequest input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.request(id, input, jwt));
    }

    @PostMapping("/commerce/seller-orders/{id}/cancellation/decision")
    public ApiResponse<Map<String, Object>> decide(@PathVariable String id, @Valid @RequestBody OrderCancellationDecision input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.decide(id, input, jwt));
    }
}
