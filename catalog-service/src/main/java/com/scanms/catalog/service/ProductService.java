package com.scanms.catalog.service;

import com.scanms.catalog.dto.request.CreateProductRequest;
import com.scanms.catalog.dto.response.ProductResponse;
import java.util.*;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);
    ProductResponse getById(UUID id);
    List<ProductResponse> findAll();
}
