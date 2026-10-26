package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.ProductMapper;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.service.ProductService;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.repository.ProductCategoryRepository;
import com.scanms.product.constant.ProductStatus;
import com.scanms.product.dto.request.UpdateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.scanms.product.config.CacheNames;
import com.scanms.product.entity.ProductCategory;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final ProductAccess access;
    private final ProductCategoryRepository categories;

    public ProductResponse create(CreateProductRequest request) {
        access.requireApprovedStore(request.storeId());
        if (request.categoryId() != null && !categories.findById(request.categoryId()).map(ProductCategory::isActive).orElse(false))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Active product category required");
        var product = mapper.toEntity(request);
        product.setStatus(request.status() == null ? ProductStatus.INACTIVE : request.status());
        product.setSizesStock(null);
        product.setImageRefs(null);
        return mapper.toResponse(repository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(String id) {
        var product=repository.findById(id).orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Product not found: "+id));
        access.requireReadableStore(product.getStoreId());
        return mapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        var rows=access.canReadAllCatalog()?repository.findAll():repository.findByStoreIdIn(access.readableStoreIds());
        return rows.stream().map(mapper::toResponse).toList();
    }

    public ProductResponse update(String id, UpdateProductRequest request) {
        var product = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + id));
        access.requireApprovedStore(product.getStoreId());
        if (request.name() != null) {
            if (request.name().isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST, "Product name is required");
            product.setName(request.name());
        }
        if (request.description() != null) product.setDescription(request.description());
        if (request.material() != null) product.setMaterial(request.material());
        if (request.category() != null) product.setCategory(request.category());
        if (request.attributes() != null) product.setAttributes(request.attributes());
        if (request.categoryId() != null) {
            if (!categories.findById(request.categoryId()).map(ProductCategory::isActive).orElse(false))
                throw new AppException(ErrorCode.INVALID_REQUEST, "Active product category required");
            product.setCategoryId(request.categoryId());
        }
        if (request.sizeGuide() != null) product.setSizeGuide(request.sizeGuide());
        if (request.priceVnd() != null) product.setPriceVnd(request.priceVnd());
        if (request.status() != null) product.setStatus(request.status());
        return mapper.toResponse(repository.save(product));
    }

    public ProductResponse deactivate(String id) {
        var product = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + id));
        access.requireApprovedStore(product.getStoreId());
        product.setStatus(ProductStatus.INACTIVE);
        return mapper.toResponse(repository.save(product));
    }
}
