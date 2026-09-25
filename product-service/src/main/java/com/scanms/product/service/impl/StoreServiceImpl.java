package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.StoreMapper;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreServiceImpl implements StoreService {
    private final StoreRepository repository;
    private final StoreMapper mapper;

    public StoreResponse create(CreateStoreRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public StoreResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
