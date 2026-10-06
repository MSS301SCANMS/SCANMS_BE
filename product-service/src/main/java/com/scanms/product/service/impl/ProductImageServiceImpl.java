package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductImageRequest;
import com.scanms.product.dto.response.ProductImageResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.ProductImageMapper;
import com.scanms.product.repository.ProductImageRepository;
import com.scanms.product.service.ProductImageService;
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

    public ProductImageResponse create(CreateProductImageRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
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
}

