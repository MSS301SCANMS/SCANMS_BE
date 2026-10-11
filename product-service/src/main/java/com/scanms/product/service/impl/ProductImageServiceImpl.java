package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductImageRequest;
import com.scanms.product.dto.response.ProductImageResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.ProductImageMapper;
import com.scanms.product.repository.ProductImageRepository;
import com.scanms.product.service.ProductImageService;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductImageServiceImpl implements ProductImageService {
    private final ProductImageRepository repository;
    private final ProductImageMapper mapper;
    private final ProductRepository products;
    private final ProductAccess access;

    public ProductImageResponse create(CreateProductImageRequest request) {
        var product = products.findLockedByProductId(request.productId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        access.requireApprovedStore(product.getStoreId());
        var existing = repository.findByProductId(request.productId());
        if (request.primaryImage()) existing.forEach(image -> image.setPrimaryImage(false));
        var image = mapper.toEntity(request);
        if (existing.isEmpty()) image.setPrimaryImage(true);
        return mapper.toResponse(repository.save(image));
    }

    @Transactional(readOnly = true)
    public ProductImageResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "ProductImage not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    public ProductImageResponse update(String id, com.scanms.product.dto.request.UpdateProductImageRequest request) {
        var image = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product image not found"));
        var product = products.findLockedByProductId(image.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        access.requireApprovedStore(product.getStoreId());
        if (request.objectKey() != null) image.setObjectKey(request.objectKey());
        if (request.imageUrl() != null) image.setImageUrl(request.imageUrl());
        if (request.sortOrder() != null) image.setSortOrder(request.sortOrder());
        if (request.altText() != null) image.setAltText(request.altText());
        if (Boolean.TRUE.equals(request.primaryImage())) {
            repository.findByProductId(image.getProductId()).forEach(other -> other.setPrimaryImage(other.getImageId().equals(id)));
        }
        return mapper.toResponse(repository.save(image));
    }

    public void delete(String id) {
        var image = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product image not found"));
        var product = products.findLockedByProductId(image.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        access.requireApprovedStore(product.getStoreId());
        repository.delete(image);
        if (image.isPrimaryImage()) repository.findByProductId(image.getProductId()).stream()
                .filter(other -> !other.getImageId().equals(id))
                .min(java.util.Comparator.comparing(other -> other.getSortOrder() == null ? Integer.MAX_VALUE : other.getSortOrder()))
                .ifPresent(next -> next.setPrimaryImage(true));
    }
}

