package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductCategoryRequest;
import com.scanms.product.dto.response.ProductCategoryResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.ProductCategoryMapper;
import com.scanms.product.repository.ProductCategoryRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.service.ProductCategoryService;
import com.scanms.product.service.ProductAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.scanms.product.dto.request.UpdateProductCategoryRequest;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCategoryServiceImpl implements ProductCategoryService {
    private final ProductCategoryRepository repository;
    private final ProductRepository products;
    private final ProductCategoryMapper mapper;
    private final ProductAccess access;

    public ProductCategoryResponse create(CreateProductCategoryRequest request) {
        access.requireManager();
        if (request.parentId() != null && !repository.existsById(request.parentId()))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Parent category not found");
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

    public ProductCategoryResponse update(String id, UpdateProductCategoryRequest request) {
        access.requireManager();
        var category = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product category not found"));
        if (request.name() != null) category.setName(request.name());
        if (request.slug() != null) category.setSlug(request.slug());
        if (request.description() != null) category.setDescription(request.description());
        if (request.active() != null) category.setActive(request.active());
        if (request.displayOrder() != null) category.setDisplayOrder(request.displayOrder());
        return mapper.toResponse(repository.save(category));
    }

    public void delete(String id) {
        access.requireManager();
        var category = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product category not found"));
        if (repository.existsByParentId(id) || products.existsByCategoryId(id))
            throw new AppException(ErrorCode.CONFLICT, "Category is used by a product or child category");
        repository.delete(category);
    }
}

