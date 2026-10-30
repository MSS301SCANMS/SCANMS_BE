package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.InventoryReservationInput;
import com.scanms.product.service.InventoryCheckoutService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
@RequiredArgsConstructor
public class InventoryCheckoutController {
    private final InventoryCheckoutService service;

    @PostMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> reserve(
            @PathVariable String orderId,
            @Valid @RequestBody InventoryReservationInput input,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.reserve(orderId, input, jwt));
    }

    @PostMapping("/quote")
    public ApiResponse<Map<String, Object>> quote(
            @Valid @RequestBody InventoryReservationInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.quote(input, jwt));
    }

    @PostMapping("/{orderId}/release")
    public ApiResponse<Map<String, Object>> release(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.release(orderId, jwt));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.cancel(orderId, jwt));
    }

    @PostMapping("/{orderId}/commit")
    public ApiResponse<Map<String, Object>> commit(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.commit(orderId, jwt));
    }
}
