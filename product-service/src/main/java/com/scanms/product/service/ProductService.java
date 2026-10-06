package com.scanms.product.service;

import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import java.util.*;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);
    ProductResponse getById(String id);
    List<ProductResponse> findAll();
}
