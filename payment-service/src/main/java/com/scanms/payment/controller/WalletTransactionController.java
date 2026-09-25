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

@RestController
@RequestMapping("/api/v1/wallet-transactions")
@RequiredArgsConstructor
public class WalletTransactionController {
    private final WalletTransactionService service;

    @PostMapping
    ResponseEntity<ApiResponse<WalletTransactionResponse>> create(
            @Valid @RequestBody CreateWalletTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<WalletTransactionResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<WalletTransactionResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

