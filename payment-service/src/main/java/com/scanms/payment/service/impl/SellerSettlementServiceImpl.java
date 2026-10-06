package com.scanms.payment.service.impl;

import com.scanms.payment.dto.request.CreateSellerSettlementRequest;
import com.scanms.payment.dto.response.SellerSettlementResponse;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.exception.ErrorCode;
import com.scanms.payment.mapper.SellerSettlementMapper;
import com.scanms.payment.repository.SellerSettlementRepository;
import com.scanms.payment.service.SellerSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SellerSettlementServiceImpl implements SellerSettlementService {
    private final SellerSettlementRepository repository;
    private final SellerSettlementMapper mapper;

    public SellerSettlementResponse create(CreateSellerSettlementRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public SellerSettlementResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "SellerSettlement not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SellerSettlementResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
