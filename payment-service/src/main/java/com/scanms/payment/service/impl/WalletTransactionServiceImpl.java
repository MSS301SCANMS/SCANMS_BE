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
    private final jakarta.persistence.EntityManager entityManager;

    public WalletTransactionResponse create(CreateWalletTransactionRequest request) {
        if (request.amountVnd() == null || request.amountVnd() <= 0 || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank() || request.type()==null || request.direction()==null || request.status() != WalletTransactionStatus.SUCCESS)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ledger requires positive integer VND and SUCCESS status");
        Wallet wallet = walletRepository.findById(request.walletId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found: " + request.walletId()));
        // Refresh while acquiring the lock: callers may already have loaded an older wallet version.
        entityManager.refresh(wallet, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var previous = repository.findByIdempotencyKey(request.idempotencyKey());
        if (previous.isPresent()) {
            WalletTransaction old = previous.get();
            if (!Objects.equals(old.getWalletId(), request.walletId()) || !Objects.equals(old.getAmountVnd(), request.amountVnd())
                    || old.getType() != request.type() || old.getDirection() != request.direction()
                    || !Objects.equals(old.getReferenceType(), request.referenceType()) || !Objects.equals(old.getReferenceId(), request.referenceId()))
                throw new AppException(ErrorCode.CONFLICT, "Idempotency key belongs to another command");
            return mapper.toResponse(old);
        }
        if (wallet.getStatus() == WalletStatus.CLOSED || (wallet.getStatus() == WalletStatus.FROZEN
                && request.direction() == WalletTransactionDirection.DEBIT
                && request.type() != WalletTransactionType.WITHDRAWAL)) {
            throw new AppException(ErrorCode.CONFLICT, "Wallet is not active");
        }

        long before = wallet.getAvailableBalanceVnd();
        long after = before;
        long heldBefore = wallet.getHeldBalanceVnd(), heldAfter = heldBefore, amount = request.amountVnd();
        if(Set.of(WalletTransactionType.TOP_UP,WalletTransactionType.REFUND,WalletTransactionType.SELLER_SETTLEMENT,
                WalletTransactionType.KOL_COMMISSION,WalletTransactionType.PLATFORM_FEE).contains(request.type())) requireDirection(request,WalletTransactionDirection.CREDIT);
        if(request.type()==WalletTransactionType.ORDER_PAYMENT) requireDirection(request,WalletTransactionDirection.DEBIT);
        try {
            switch (request.type()) {
                case HOLD -> {
                    requireDirection(request, WalletTransactionDirection.DEBIT);
                    after = Math.subtractExact(before, amount); heldAfter = Math.addExact(heldBefore, amount);
                }
                case RELEASE -> {
                    requireDirection(request, WalletTransactionDirection.CREDIT);
                    after = Math.addExact(before, amount); heldAfter = Math.subtractExact(heldBefore, amount);
                }
                case WITHDRAWAL -> {
                    requireDirection(request, WalletTransactionDirection.DEBIT);
                    heldAfter = Math.subtractExact(heldBefore, amount);
                }
                default -> after = request.direction() == WalletTransactionDirection.CREDIT
                        ? Math.addExact(before, amount) : Math.subtractExact(before, amount);
            }
        } catch (ArithmeticException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Amount overflow");
        }
        if (after < 0 || heldAfter < 0) throw new AppException(ErrorCode.CONFLICT, "Insufficient wallet balance");
        if(after>9_007_199_254_740_991L || heldAfter>9_007_199_254_740_991L) throw new AppException(ErrorCode.CONFLICT,"Wallet exceeds the supported exact VND display range");
        wallet.setAvailableBalanceVnd(after); wallet.setHeldBalanceVnd(heldAfter);
        walletRepository.save(wallet);
        WalletTransaction transaction = mapper.toEntity(request);
        transaction.setBalanceBeforeVnd(before);
        transaction.setBalanceAfterVnd(after);
        transaction.setHeldBeforeVnd(heldBefore); transaction.setHeldAfterVnd(heldAfter);
        transaction.setCompletedAt(java.time.Instant.now());
        return mapper.toResponse(repository.saveAndFlush(transaction));
    }

    private void requireDirection(CreateWalletTransactionRequest r, WalletTransactionDirection expected) {
        if (r.direction() != expected) throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid bucket movement direction");
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

