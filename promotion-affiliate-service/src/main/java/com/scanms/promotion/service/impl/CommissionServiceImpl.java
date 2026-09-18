package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateCommissionRequest;
import com.scanms.promotion.dto.response.CommissionResponse;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.CommissionMapper;
import com.scanms.promotion.repository.CommissionRepository;
import com.scanms.promotion.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CommissionServiceImpl implements CommissionService {
    private final CommissionRepository repository;
    private final CommissionMapper mapper;

    public CommissionResponse create(CreateCommissionRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public CommissionResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Commission not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CommissionResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
