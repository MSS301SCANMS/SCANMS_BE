package com.scanms.catalog.service.impl;

import com.scanms.catalog.dto.request.CreateStoreRequest;
import com.scanms.catalog.dto.response.StoreResponse;
import com.scanms.catalog.exception.AppException;
import com.scanms.catalog.exception.ErrorCode;
import com.scanms.catalog.mapper.StoreMapper;
import com.scanms.catalog.repository.StoreRepository;
import com.scanms.catalog.service.StoreService;
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
    public StoreResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
