package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.ProductVariantMapper;
import com.scanms.product.repository.ProductVariantRepository;
import com.scanms.product.service.ProductVariantService;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.dto.request.UpdateProductVariantRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.scanms.product.constant.ProductVariantStatus;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductVariantRepository repository;
    private final ProductVariantMapper mapper;
    private final ProductRepository products;
    private final ProductAccess access;

    public ProductVariantResponse create(CreateProductVariantRequest request) {
        var product = products.findById(request.productId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        access.requireApprovedStore(product.getStoreId());
        if (request.priceVnd() == null || request.priceVnd() <= 0 || request.stockQuantity() == null || request.stockQuantity() < 0)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Variant price and stock are invalid");
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "ProductVariant not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    public ProductVariantResponse update(String id, UpdateProductVariantRequest request) {
        var variant = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Variant not found: " + id));
        var product = products.findById(variant.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        access.requireApprovedStore(product.getStoreId());
        if (request.priceVnd() != null) {
            if (request.priceVnd() <= 0) throw new AppException(ErrorCode.INVALID_REQUEST, "Price must be positive");
            variant.setPriceVnd(request.priceVnd());
        }
        if (request.stockQuantity() != null) {
            if (request.stockQuantity() < 0) throw new AppException(ErrorCode.INVALID_REQUEST, "Stock cannot be negative");
            variant.setStockQuantity(request.stockQuantity());
        }
        if (request.status() != null) variant.setStatus(request.status());
        if (request.size() != null) variant.setSize(request.size());
        if (request.color() != null) variant.setColor(request.color());
        return mapper.toResponse(repository.save(variant));
    }

    public ProductVariantResponse deactivate(String id) {
        var variant = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Variant not found: " + id));
        var product = products.findById(variant.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        access.requireApprovedStore(product.getStoreId());
        variant.setStatus(ProductVariantStatus.INACTIVE);
        return mapper.toResponse(repository.save(variant));
    }
}

