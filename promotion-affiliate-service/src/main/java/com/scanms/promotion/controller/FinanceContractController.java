package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.*;
import com.scanms.promotion.service.FinanceContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceContractController {
    private final FinanceContractService financeContractService;

    @GetMapping("/commission-basis/{id}")
    public ApiResponse<Map<String, Object>> basis(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(financeContractService.basis(id, jwt));
    }

    @PostMapping("/commissions/{id}/credited")
    public ApiResponse<Map<String, Object>> credited(
            @PathVariable String id, @RequestBody CreditInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(financeContractService.credited(id, input, jwt));
    }

    @GetMapping("/commissions-total")
    public ApiResponse<Map<String, Object>> total(
            @RequestParam String orderItemIds, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(financeContractService.total(orderItemIds, jwt));
    }

    @PostMapping("/commission-initializations/{itemId}")
    public ApiResponse<Map<String, Object>> initializeCommission(
            @PathVariable String itemId,
            @RequestBody(required = false) InitInput input,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(financeContractService.initializeCommission(itemId, input, jwt));
    }

    @PostMapping("/commission-adjustments/{itemId}")
    public ApiResponse<Map<String, Object>> adjustCommission(
            @PathVariable String itemId,
            @RequestBody(required = false) AdjustInput input,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(financeContractService.adjustCommission(itemId, input, jwt));
    }

    @PostMapping("/commission-finalizations/{itemId}")
    public ApiResponse<Map<String, Object>> finalizeItem(
            @PathVariable String itemId, @RequestBody FinalizeInput input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(financeContractService.finalizeItem(itemId, input, jwt));
    }
}
