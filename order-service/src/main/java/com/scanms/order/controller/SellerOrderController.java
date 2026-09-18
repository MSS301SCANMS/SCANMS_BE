package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
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
    ResponseEntity<ApiResponse<SellerOrderResponse>> create(@Valid @RequestBody CreateSellerOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<SellerOrderResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<SellerOrderResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
