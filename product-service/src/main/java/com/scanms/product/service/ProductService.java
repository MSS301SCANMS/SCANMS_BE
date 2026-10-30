package com.scanms.product.service;

import com.scanms.product.constant.ProductStatus;
import com.scanms.product.dto.PageResponse;
import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.request.UpdateProductRequest;
import com.scanms.product.dto.response.ProductResponse;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);

    ProductResponse getById(String id);

    PageResponse<ProductResponse> findAll(int page, int size, String storeId, ProductStatus status, String search);

    ProductResponse update(String id, UpdateProductRequest request);

    ProductResponse deactivate(String id);
}
