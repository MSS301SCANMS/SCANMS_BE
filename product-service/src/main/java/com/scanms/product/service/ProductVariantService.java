package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import java.util.*;

public interface ProductVariantService {
    ProductVariantResponse create(CreateProductVariantRequest request);
    ProductVariantResponse getById(String id);
    List<ProductVariantResponse> findAll();
    ProductVariantResponse update(String id, com.scanms.product.dto.request.UpdateProductVariantRequest request);
    ProductVariantResponse deactivate(String id);
}

