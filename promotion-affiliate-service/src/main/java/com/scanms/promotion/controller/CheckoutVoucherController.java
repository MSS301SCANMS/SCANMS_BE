package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.CheckoutVoucherInput;
import com.scanms.promotion.service.CheckoutVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkout-vouchers")
@RequiredArgsConstructor
public class CheckoutVoucherController {
    private final CheckoutVoucherService checkoutVoucherService;

    @PostMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> reserve(
            @PathVariable String orderId, @RequestBody CheckoutVoucherInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(checkoutVoucherService.reserve(orderId, input, jwt));
    }

    @PostMapping("/quote")
    public ApiResponse<Map<String, Object>> quote(@RequestBody CheckoutVoucherInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(checkoutVoucherService.quote(input, jwt));
    }

    @PostMapping("/{orderId}/release")
    public ApiResponse<Map<String, Object>> release(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(checkoutVoucherService.release(orderId, jwt));
    }

    @PostMapping("/{orderId}/commit")
    public ApiResponse<Map<String, Object>> commit(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(checkoutVoucherService.commit(orderId, jwt));
    }

    @PostMapping("/{orderId}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(checkoutVoucherService.restore(orderId, jwt));
    }
}
