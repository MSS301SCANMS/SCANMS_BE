package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductCategoryRequest;
import com.scanms.product.dto.response.ProductCategoryResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.ProductCategoryMapper;
import com.scanms.product.repository.ProductCategoryRepository;
import com.scanms.product.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCategoryServiceImpl implements ProductCategoryService {
    private final ProductCategoryRepository repository;
    private final ProductCategoryMapper mapper;

    public ProductCategoryResponse create(CreateProductCategoryRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "ProductCategory not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

