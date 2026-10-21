package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.dto.response.StoreApplicationResponse;
import com.scanms.product.service.StoreApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/store-applications")
@RequiredArgsConstructor
public class StoreApplicationController {
    private final StoreApplicationService service;

    public record ReviewRequest(String note) {}

    @PostMapping
    ResponseEntity<ApiResponse<StoreApplicationResponse>> create(@Valid @RequestBody CreateStoreApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<StoreApplicationResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @PutMapping("/{id}")
    ApiResponse<StoreApplicationResponse> updatePending(@PathVariable String id, @Valid @RequestBody CreateStoreApplicationRequest request) {
        return ApiResponse.success(service.updatePending(id, request));
    }

    @GetMapping
    ApiResponse<List<StoreApplicationResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PostMapping("/{id}/approve")
    ApiResponse<StoreApplicationResponse> approve(@PathVariable String id, @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.success(service.review(id, true, request == null ? null : request.note()));
    }

    @PostMapping("/{id}/reject")
    ApiResponse<StoreApplicationResponse> reject(@PathVariable String id, @RequestBody(required = false) ReviewRequest request) {
        return ApiResponse.success(service.review(id, false, request == null ? null : request.note()));
    }
}

