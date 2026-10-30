package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.PaidInput;
import com.scanms.order.dto.request.RefundInput;
import com.scanms.order.service.FinanceContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/api/v1/finance") @RequiredArgsConstructor
public class FinanceContractController {
    private final FinanceContractService service;

    @PostMapping("/orders/{id}/paid")
    public ApiResponse<Map<String, Object>> paid(@PathVariable String id, @RequestBody PaidInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.paid(id, input, jwt));
    }

    @GetMapping("/refund-basis/{id}")
    public ApiResponse<Map<String, Object>> refundBasis(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.refundBasis(id, jwt));
    }

    @GetMapping("/orders/{id}/unapplied-payment")
    public ApiResponse<Map<String, Object>> unapplied(@PathVariable String id, @RequestParam String paymentId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.unapplied(id, paymentId, jwt));
    }

    @PostMapping("/returns/{id}/refunded")
    public ApiResponse<Map<String, Object>> refunded(@PathVariable String id, @RequestBody RefundInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.refunded(id, input, jwt));
    }

    @GetMapping("/settlement-basis/{id}")
    public ApiResponse<Map<String, Object>> settlementBasis(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.settlementBasis(id, jwt));
    }

    @GetMapping("/commission-order-item/{id}")
    public ApiResponse<Map<String, Object>> commissionItem(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.commissionItem(id, jwt));
    }
}
