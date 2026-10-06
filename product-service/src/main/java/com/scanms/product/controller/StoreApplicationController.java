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

    @PostMapping
    ResponseEntity<ApiResponse<StoreApplicationResponse>> create(@Valid @RequestBody CreateStoreApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<StoreApplicationResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<StoreApplicationResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

