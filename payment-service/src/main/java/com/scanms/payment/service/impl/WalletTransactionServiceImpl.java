package com.scanms.payment.service.impl;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.dto.response.WalletTransactionResponse;
import com.scanms.payment.entity.*;
import com.scanms.payment.exception.*;
import com.scanms.payment.mapper.WalletTransactionMapper;
import com.scanms.payment.repository.*;
import com.scanms.payment.service.WalletTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletTransactionServiceImpl implements WalletTransactionService {
    private final WalletTransactionRepository repository;
    private final WalletRepository walletRepository;
    private final WalletTransactionMapper mapper;

    public WalletTransactionResponse create(CreateWalletTransactionRequest request) {
        if (repository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new AppException(ErrorCode.CONFLICT, "Wallet transaction idempotency key already exists");
        }
        Wallet wallet = walletRepository.findByIdForUpdate(request.walletId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found: " + request.walletId()));
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new AppException(ErrorCode.CONFLICT, "Wallet is not active");
        }

        long before = wallet.getAvailableBalanceVnd();
        long after = before;
        if (request.status() == WalletTransactionStatus.SUCCESS) {
            after = request.direction() == WalletTransactionDirection.CREDIT
                    ? Math.addExact(before, request.amountVnd())
                    : Math.subtractExact(before, request.amountVnd());
            if (after < 0) {
                throw new AppException(ErrorCode.CONFLICT, "Insufficient wallet balance");
            }
            wallet.setAvailableBalanceVnd(after);
            walletRepository.save(wallet);
        }

        WalletTransaction transaction = mapper.toEntity(request);
        transaction.setBalanceBeforeVnd(before);
        transaction.setBalanceAfterVnd(after);
        return mapper.toResponse(repository.save(transaction));
    }

    @Transactional(readOnly = true)
    public WalletTransactionResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "WalletTransaction not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

