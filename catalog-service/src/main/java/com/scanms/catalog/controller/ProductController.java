package com.scanms.catalog.controller;

import com.scanms.catalog.dto.ApiResponse;
import com.scanms.catalog.dto.request.CreateProductRequest;
import com.scanms.catalog.dto.response.ProductResponse;
import com.scanms.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @PostMapping
    ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<ProductResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<ProductResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
