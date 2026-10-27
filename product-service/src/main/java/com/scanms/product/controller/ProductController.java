package com.scanms.product.controller;

import com.scanms.product.constant.ProductStatus;
import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.PageResponse;
import com.scanms.product.dto.ai.ShopAiAnalyzeResponse;
import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.request.UpdateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import com.scanms.product.service.AiAnalysisService;
import com.scanms.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    private final AiAnalysisService aiAnalysisService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String search) {
        int pageSize = limit != null ? limit : size;
        return ApiResponse.success(service.findAll(page, pageSize, storeId, status, search));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<ProductResponse> deactivate(@PathVariable String id) {
        return ApiResponse.success(service.deactivate(id));
    }

    /** Shop owner: upload product cover image → AI detects clothing and returns form hints. */
    @PostMapping(value = "/ai-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ShopAiAnalyzeResponse> analyzeImage(@RequestPart("image") MultipartFile image) {
        return ApiResponse.success(aiAnalysisService.analyze(image));
    }
}
