package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.dto.response.WalletTransactionResponse;
import com.scanms.payment.service.WalletTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/wallet-transactions")
@RequiredArgsConstructor
public class WalletTransactionController {
    private final WalletTransactionService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PAYMENT_INTERNAL')")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> create(
            @Valid @RequestBody CreateWalletTransactionRequest request) {
        if(request.type()==com.scanms.payment.constant.WalletTransactionType.COMMISSION_REVERSAL)
            throw new com.scanms.payment.exception.AppException(com.scanms.payment.exception.ErrorCode.FORBIDDEN,"Use source-verified commission reconciliation");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'PAYMENT_INTERNAL')")
    public ApiResponse<WalletTransactionResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'PAYMENT_INTERNAL')")
    public ApiResponse<List<WalletTransactionResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

