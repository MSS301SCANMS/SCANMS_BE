package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.dto.response.StoreApplicationResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.StoreApplicationMapper;
import com.scanms.product.repository.StoreApplicationRepository;
import com.scanms.product.service.StoreApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreApplicationServiceImpl implements StoreApplicationService {
    private final StoreApplicationRepository repository;
    private final StoreApplicationMapper mapper;

    public StoreApplicationResponse create(CreateStoreApplicationRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public StoreApplicationResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "StoreApplication not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StoreApplicationResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

