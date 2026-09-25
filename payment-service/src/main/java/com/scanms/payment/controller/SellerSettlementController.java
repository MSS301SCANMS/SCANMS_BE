package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.dto.request.CreateSellerSettlementRequest;
import com.scanms.payment.dto.response.SellerSettlementResponse;
import com.scanms.payment.service.SellerSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SellerSettlementController {
    private final SellerSettlementService service;

    @PostMapping
    ResponseEntity<ApiResponse<SellerSettlementResponse>> create(@Valid @RequestBody CreateSellerSettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<SellerSettlementResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<SellerSettlementResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
