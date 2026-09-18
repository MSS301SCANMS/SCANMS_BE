package com.scanms.payment.service.impl;

import com.scanms.payment.dto.request.RegisterBankAccountRequest;
import com.scanms.payment.dto.response.BankAccountResponse;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.exception.ErrorCode;
import com.scanms.payment.mapper.BankAccountMapper;
import com.scanms.payment.repository.BankAccountRepository;
import com.scanms.payment.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BankAccountServiceImpl implements BankAccountService {
    private final BankAccountRepository repository;
    private final BankAccountMapper mapper;

    public BankAccountResponse create(RegisterBankAccountRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public BankAccountResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "BankAccount not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
