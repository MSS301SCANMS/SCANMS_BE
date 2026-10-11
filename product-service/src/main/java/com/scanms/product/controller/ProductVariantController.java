package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import com.scanms.product.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/product-variants")
@RequiredArgsConstructor
public class ProductVariantController {
    private final ProductVariantService service;

    @PostMapping
    ResponseEntity<ApiResponse<ProductVariantResponse>> create(@Valid @RequestBody CreateProductVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<ProductVariantResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<ProductVariantResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PatchMapping("/{id}")
    ApiResponse<ProductVariantResponse> update(@PathVariable String id, @Valid @RequestBody com.scanms.product.dto.request.UpdateProductVariantRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    ApiResponse<ProductVariantResponse> deactivate(@PathVariable String id) {
        return ApiResponse.success(service.deactivate(id));
    }
}

