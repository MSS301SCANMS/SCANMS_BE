package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.CreateCommissionRequest;
import com.scanms.promotion.dto.response.CommissionResponse;
import com.scanms.promotion.service.CommissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/commissions")
@RequiredArgsConstructor
public class CommissionController {
    private final CommissionService service;

    @PostMapping
    ResponseEntity<ApiResponse<CommissionResponse>> create(@Valid @RequestBody CreateCommissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<CommissionResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<CommissionResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
