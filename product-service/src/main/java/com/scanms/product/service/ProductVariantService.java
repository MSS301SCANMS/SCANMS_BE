package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import java.util.*;
import com.scanms.product.dto.request.UpdateProductVariantRequest;

public interface ProductVariantService {
    ProductVariantResponse create(CreateProductVariantRequest request);
    ProductVariantResponse getById(String id);
    List<ProductVariantResponse> findAll();
    ProductVariantResponse update(String id, UpdateProductVariantRequest request);
    ProductVariantResponse deactivate(String id);
}

