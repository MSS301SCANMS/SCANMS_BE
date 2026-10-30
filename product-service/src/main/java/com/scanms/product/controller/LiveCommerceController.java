package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.*;
import com.scanms.product.dto.response.LiveCommerceResponse;
import com.scanms.product.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/livestreams/{id}")
@RequiredArgsConstructor
public class LiveCommerceController {
    private final LiveCommerceService service;
    private final LivePurchaseAccess purchases;
    private final LiveMetricsSignature signature;

    @GetMapping("/commerce")
    public ApiResponse<LiveCommerceResponse> getCommerce(@PathVariable String id) {
        return ApiResponse.success(service.get(id, false));
    }

    @GetMapping("/commerce/manage")
    public ApiResponse<LiveCommerceResponse> getManagementState(@PathVariable String id) {
        return ApiResponse.success(service.get(id, true));
    }

    @PostMapping("/offers")
    public ResponseEntity<ApiResponse<LiveCommerceResponse>> createOffer(
            @PathVariable String id, @Valid @RequestBody CreateLiveOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.createOffer(id, request)));
    }

    @PostMapping("/offers/{offerId}/stop")
    public ApiResponse<LiveCommerceResponse> stopOffer(@PathVariable String id, @PathVariable String offerId) {
        return ApiResponse.success(service.stopOffer(id, offerId));
    }

    @PostMapping("/voucher-drops")
    public ResponseEntity<ApiResponse<LiveCommerceResponse>> createVoucherDrop(
            @PathVariable String id, @Valid @RequestBody CreateLiveVoucherDropRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.createVoucherDrop(id, request)));
    }

    @PostMapping("/voucher-drops/{dropId}/cancel")
    public ApiResponse<LiveCommerceResponse> cancelVoucherDrop(@PathVariable String id, @PathVariable String dropId) {
        return ApiResponse.success(service.cancelVoucherDrop(id, dropId));
    }

    @PostMapping("/purchase-access")
    public ApiResponse<Map<String, String>> purchaseAccess(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Live-Metrics-Time", defaultValue = "") String time,
            @RequestHeader(value = "X-Live-Metrics-Signature", defaultValue = "") String proof) {
        signature.require("ACCESS\n" + id, time, proof);
        return ApiResponse.success(Map.of("token", purchases.issue(id, jwt)));
    }
}
