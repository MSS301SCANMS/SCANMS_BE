package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import java.util.*;

public interface ProductVariantService {
    ProductVariantResponse create(CreateProductVariantRequest request);
    ProductVariantResponse getById(String id);
    List<ProductVariantResponse> findAll();
}

