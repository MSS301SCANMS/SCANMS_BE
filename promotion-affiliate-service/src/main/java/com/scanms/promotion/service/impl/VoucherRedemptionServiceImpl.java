package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateVoucherRedemptionRequest;
import com.scanms.promotion.dto.response.VoucherRedemptionResponse;
import com.scanms.promotion.exception.*;
import com.scanms.promotion.mapper.VoucherRedemptionMapper;
import com.scanms.promotion.repository.VoucherRedemptionRepository;
import com.scanms.promotion.service.VoucherRedemptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class VoucherRedemptionServiceImpl implements VoucherRedemptionService {
    private final VoucherRedemptionRepository repository;
    private final VoucherRedemptionMapper mapper;
    public VoucherRedemptionResponse create(CreateVoucherRedemptionRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public VoucherRedemptionResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "VoucherRedemption not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<VoucherRedemptionResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

