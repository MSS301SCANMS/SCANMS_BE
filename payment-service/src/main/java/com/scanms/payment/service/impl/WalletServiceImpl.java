package com.scanms.payment.service.impl;

import com.scanms.payment.dto.request.CreateWalletRequest;
import com.scanms.payment.dto.response.WalletResponse;
import com.scanms.payment.exception.*;
import com.scanms.payment.mapper.WalletMapper;
import com.scanms.payment.repository.WalletRepository;
import com.scanms.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletServiceImpl implements WalletService {
    private final WalletRepository repository;
    private final WalletMapper mapper;
    public WalletResponse create(CreateWalletRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public WalletResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<WalletResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

