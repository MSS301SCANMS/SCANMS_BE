package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.ProductMapper;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductResponse create(CreateProductRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
