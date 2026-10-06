package com.scanms.order.controller;

import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
import com.scanms.order.dto.response.SellerOrderSummaryResponse;
import com.scanms.order.service.SellerOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/seller-orders")
@RequiredArgsConstructor
public class SellerOrderController {
    private final SellerOrderService service;

    @PostMapping
    public ResponseEntity<ApiResponse<SellerOrderResponse>> create(@Valid @RequestBody CreateSellerOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<SellerOrderResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/{id}/summary")
    public ApiResponse<SellerOrderSummaryResponse> getSummaryById(@PathVariable String id) {
        return ApiResponse.success(service.getSummaryById(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<SellerOrderResponse> updateStatus(
            @PathVariable String id,
            @RequestParam SellerOrderStatus status) {
        return ApiResponse.success(service.updateStatus(id, status));
    }

    @GetMapping
    public ApiResponse<List<SellerOrderResponse>> findAll(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) SellerOrderStatus status) {
        if (storeId != null && !storeId.isBlank()) {
            return ApiResponse.success(service.findByStore(storeId, status));
        }
        return ApiResponse.success(service.findAll());
    }
}
