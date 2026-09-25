package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.ai.AiDetectionResponse;
import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import com.scanms.product.service.AiAnalysisService;
import com.scanms.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    private final AiAnalysisService aiAnalysisService;

    @PostMapping
    ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<ProductResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<ProductResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PostMapping(value = "/ai-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AiDetectionResponse> analyzeImage(@RequestPart("image") MultipartFile image) {
        return ApiResponse.success(aiAnalysisService.analyze(image));
    }
}
