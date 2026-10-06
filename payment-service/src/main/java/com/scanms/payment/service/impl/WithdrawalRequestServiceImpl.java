package com.scanms.payment.service.impl;

import com.scanms.payment.dto.request.CreateWithdrawalRequest;
import com.scanms.payment.dto.response.WithdrawalRequestResponse;
import com.scanms.payment.exception.*;
import com.scanms.payment.mapper.WithdrawalRequestMapper;
import com.scanms.payment.repository.WithdrawalRequestRepository;
import com.scanms.payment.service.WithdrawalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalRequestServiceImpl implements WithdrawalRequestService {
    private final WithdrawalRequestRepository repository;
    private final WithdrawalRequestMapper mapper;
    public WithdrawalRequestResponse create(CreateWithdrawalRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public WithdrawalRequestResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "WithdrawalRequest not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<WithdrawalRequestResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
