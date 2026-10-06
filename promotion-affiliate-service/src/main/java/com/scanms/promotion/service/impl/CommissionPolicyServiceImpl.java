package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import com.scanms.promotion.exception.*;
import com.scanms.promotion.mapper.CommissionPolicyMapper;
import com.scanms.promotion.repository.CommissionPolicyRepository;
import com.scanms.promotion.service.CommissionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CommissionPolicyServiceImpl implements CommissionPolicyService {
    private final CommissionPolicyRepository repository;
    private final CommissionPolicyMapper mapper;
    public CommissionPolicyResponse create(CreateCommissionPolicyRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public CommissionPolicyResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "CommissionPolicy not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<CommissionPolicyResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

