package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.ProductVariantMapper;
import com.scanms.product.repository.ProductVariantRepository;
import com.scanms.product.service.ProductVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductVariantServiceImpl implements ProductVariantService {
    private final ProductVariantRepository repository;
    private final ProductVariantMapper mapper;

    public ProductVariantResponse create(CreateProductVariantRequest request) {
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
}

