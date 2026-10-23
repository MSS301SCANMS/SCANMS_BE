package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductImageRequest;
import com.scanms.product.dto.request.UpdateProductImageRequest;
import com.scanms.product.dto.response.ProductImageResponse;

import java.util.List;

public interface ProductImageService {
    ProductImageResponse create(CreateProductImageRequest request);

    ProductImageResponse getById(String id);

    List<ProductImageResponse> findAll();

    ProductImageResponse update(String id, UpdateProductImageRequest request);

    void delete(String id);
}

