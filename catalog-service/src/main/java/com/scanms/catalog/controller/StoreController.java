package com.scanms.catalog.controller;

import com.scanms.catalog.dto.ApiResponse;
import com.scanms.catalog.dto.request.CreateStoreRequest;
import com.scanms.catalog.dto.response.StoreResponse;
import com.scanms.catalog.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService service;

    @PostMapping
    ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<StoreResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<StoreResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
