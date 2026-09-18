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

@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService service;

    @PostMapping
    ResponseEntity<ApiResponse<BankAccountResponse>> create(@Valid @RequestBody RegisterBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<BankAccountResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<BankAccountResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
