package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import java.util.*;
import com.scanms.product.dto.request.UpdateProductRequest;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);
    ProductResponse getById(String id);
    List<ProductResponse> findAll();
    ProductResponse update(String id, UpdateProductRequest request);
    ProductResponse deactivate(String id);
}
