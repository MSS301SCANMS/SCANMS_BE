package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import com.scanms.order.service.DiscountAllocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/discount-allocations")
@RequiredArgsConstructor
public class DiscountAllocationController {
    private final DiscountAllocationService service;
    @PostMapping
    ResponseEntity<ApiResponse<DiscountAllocationResponse>> create(@Valid @RequestBody CreateDiscountAllocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }
    @GetMapping("/{id}")
    ApiResponse<DiscountAllocationResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }
    @GetMapping
    ApiResponse<List<DiscountAllocationResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

