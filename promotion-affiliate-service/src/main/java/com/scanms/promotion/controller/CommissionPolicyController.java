package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import com.scanms.promotion.service.CommissionPolicyService;
import com.scanms.promotion.service.VoucherAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/commission-policies")
@RequiredArgsConstructor
public class CommissionPolicyController {
    private final CommissionPolicyService service;
    private final VoucherAccess access;
    @PostMapping
    ResponseEntity<ApiResponse<CommissionPolicyResponse>> create(@Valid @RequestBody CreateCommissionPolicyRequest request) {
        if (!access.isManager() && request.scopeType() == com.scanms.promotion.constant.CommissionPolicyScope.STORE
                && request.storeId() != null && !request.storeId().isBlank()) {
            access.requireStoreOwner(request.storeId());
        } else {
            access.requireManager();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }
    @PutMapping("/{id}")
    ApiResponse<CommissionPolicyResponse> update(@PathVariable String id, @RequestParam Long version,
            @Valid @RequestBody CreateCommissionPolicyRequest request) {
        access.requireManager();
        return ApiResponse.success(service.update(id, version, request));
    }
    @GetMapping("/{id}")
    ApiResponse<CommissionPolicyResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }
    @GetMapping
    ApiResponse<List<CommissionPolicyResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

