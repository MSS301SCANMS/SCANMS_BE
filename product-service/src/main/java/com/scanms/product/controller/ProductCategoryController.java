package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateProductCategoryRequest;
import com.scanms.product.dto.response.ProductCategoryResponse;
import com.scanms.product.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
public class ProductCategoryController {
    private final ProductCategoryService service;

    @PostMapping
    ResponseEntity<ApiResponse<ProductCategoryResponse>> create(@Valid @RequestBody CreateProductCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<ProductCategoryResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<ProductCategoryResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}

