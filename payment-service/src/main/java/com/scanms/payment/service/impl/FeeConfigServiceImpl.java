package com.scanms.payment.service.impl;

import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.FeeConfigResponse;
import com.scanms.payment.exception.*;
import com.scanms.payment.mapper.FeeConfigMapper;
import com.scanms.payment.repository.FeeConfigRepository;
import com.scanms.payment.service.FeeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class FeeConfigServiceImpl implements FeeConfigService {
    private final FeeConfigRepository repository;
    private final FeeConfigMapper mapper;
    public FeeConfigResponse create(CreateFeeConfigRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public FeeConfigResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "FeeConfig not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<FeeConfigResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

