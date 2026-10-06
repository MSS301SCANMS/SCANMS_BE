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
    private final com.scanms.payment.service.PaymentAccess access;
    public WalletResponse create(CreateWalletRequest request) {
        access.owner(request.ownerType(), request.ownerRefId());
        if (request.availableBalanceVnd() != 0 || request.heldBalanceVnd() != 0 || request.status() != com.scanms.payment.constant.WalletStatus.ACTIVE
                || !"VND".equals(request.currency())) throw new AppException(ErrorCode.INVALID_REQUEST, "New wallets must be ACTIVE with zero VND balances");
        return mapper.toResponse(repository.findByOwnerTypeAndOwnerRefIdAndCurrency(request.ownerType(), request.ownerRefId(), "VND")
                .orElseGet(() -> repository.save(mapper.toEntity(request))));
    }
    @Transactional(readOnly = true)
    public WalletResponse getById(String id) {
        var wallet = repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
        access.wallet(wallet); return mapper.toResponse(wallet);
    }
    @Transactional(readOnly = true)
    public List<WalletResponse> findAll() {
        access.operatorOnly();
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

