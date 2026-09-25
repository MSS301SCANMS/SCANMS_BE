package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import com.scanms.order.exception.*;
import com.scanms.order.mapper.DiscountAllocationMapper;
import com.scanms.order.repository.DiscountAllocationRepository;
import com.scanms.order.service.DiscountAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscountAllocationServiceImpl implements DiscountAllocationService {
    private final DiscountAllocationRepository repository;
    private final DiscountAllocationMapper mapper;
    public DiscountAllocationResponse create(CreateDiscountAllocationRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public DiscountAllocationResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "DiscountAllocation not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<DiscountAllocationResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

