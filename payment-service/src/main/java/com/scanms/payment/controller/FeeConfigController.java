package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.FeeConfigResponse;
import com.scanms.payment.service.FeeConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'PAYMENT_INTERNAL')")
@RestController
@RequestMapping("/api/v1/fee-configs")
@RequiredArgsConstructor
public class FeeConfigController {
    private final FeeConfigService service;
    @org.springframework.security.access.prepost.PreAuthorize("denyAll()")
    @PostMapping
    ResponseEntity<ApiResponse<FeeConfigResponse>> create(@Valid @RequestBody CreateFeeConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }
    @GetMapping("/{id}")
    ApiResponse<FeeConfigResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }
    @GetMapping
    ApiResponse<List<FeeConfigResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

