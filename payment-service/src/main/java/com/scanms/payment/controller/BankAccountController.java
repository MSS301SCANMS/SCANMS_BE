package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.dto.request.RegisterBankAccountRequest;
import com.scanms.payment.dto.response.BankAccountResponse;
import com.scanms.payment.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'PAYMENT_INTERNAL')")
@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService service;

    @org.springframework.security.access.prepost.PreAuthorize("denyAll()")
    @PostMapping
    ResponseEntity<ApiResponse<BankAccountResponse>> create(@Valid @RequestBody RegisterBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<BankAccountResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<BankAccountResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
