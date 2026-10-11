package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateProductImageRequest;
import com.scanms.product.dto.response.ProductImageResponse;
import com.scanms.product.service.ProductImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/product-images")
@RequiredArgsConstructor
public class ProductImageController {
    private final ProductImageService service;

    @PostMapping
    ResponseEntity<ApiResponse<ProductImageResponse>> create(@Valid @RequestBody CreateProductImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<ProductImageResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<ProductImageResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PatchMapping("/{id}")
    ApiResponse<ProductImageResponse> update(@PathVariable String id, @Valid @RequestBody com.scanms.product.dto.request.UpdateProductImageRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

