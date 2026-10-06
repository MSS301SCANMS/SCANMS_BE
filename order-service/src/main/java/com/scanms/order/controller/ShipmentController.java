package com.scanms.order.controller;

import com.scanms.order.constant.ShipmentStatus;
import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import com.scanms.order.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentService service;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentResponse>> create(@Valid @RequestBody CreateShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShipmentResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/by-seller-order/{sellerOrderId}")
    public ApiResponse<ShipmentResponse> getBySellerOrderId(@PathVariable String sellerOrderId) {
        return ApiResponse.success(service.getBySellerOrderId(sellerOrderId));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ShipmentResponse> updateStatus(
            @PathVariable String id,
            @RequestParam ShipmentStatus status,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String failureReason) {
        return ApiResponse.success(service.updateShipmentStatus(id, status, carrier, trackingCode, failureReason));
    }

    @GetMapping
    public ApiResponse<List<ShipmentResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
