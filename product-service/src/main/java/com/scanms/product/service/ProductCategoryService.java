package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductCategoryRequest;
import com.scanms.product.dto.response.ProductCategoryResponse;
import java.util.*;

public interface ProductCategoryService {
    ProductCategoryResponse create(CreateProductCategoryRequest request);
    ProductCategoryResponse getById(String id);
    List<ProductCategoryResponse> findAll();
}

