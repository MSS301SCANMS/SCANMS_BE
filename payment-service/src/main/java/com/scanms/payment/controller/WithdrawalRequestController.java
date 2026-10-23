package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.dto.request.CreateWithdrawalRequest;
import com.scanms.payment.dto.response.WithdrawalRequestResponse;
import com.scanms.payment.service.WithdrawalRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'PAYMENT_INTERNAL')")
@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalRequestController {
    private final WithdrawalRequestService service;
    @PreAuthorize("denyAll()")
    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalRequestResponse>> create(@Valid @RequestBody CreateWithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }
    @GetMapping("/{id}")
    public ApiResponse<WithdrawalRequestResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }
    @GetMapping
    public ApiResponse<List<WithdrawalRequestResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
