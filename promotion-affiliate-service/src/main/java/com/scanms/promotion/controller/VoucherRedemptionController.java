package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.CreateVoucherRedemptionRequest;
import com.scanms.promotion.dto.response.VoucherRedemptionResponse;
import com.scanms.promotion.service.VoucherRedemptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/voucher-redemptions")
@RequiredArgsConstructor
public class VoucherRedemptionController {
    private final VoucherRedemptionService service;
    @PostMapping
    ResponseEntity<ApiResponse<VoucherRedemptionResponse>> create(@Valid @RequestBody CreateVoucherRedemptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }
    @GetMapping("/{id}")
    ApiResponse<VoucherRedemptionResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }
    @GetMapping
    ApiResponse<List<VoucherRedemptionResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

