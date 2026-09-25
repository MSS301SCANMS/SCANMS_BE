package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.dto.response.WalletTransactionResponse;
import com.scanms.payment.entity.WalletTransaction;
import org.springframework.stereotype.Component;

@Component
public class WalletTransactionMapper {
    public WalletTransaction toEntity(CreateWalletTransactionRequest request) {
        return WalletTransaction.builder()
                .walletId(request.walletId())
                .type(request.type())
                .direction(request.direction())
                .amountVnd(request.amountVnd())
                .status(request.status())
                .referenceType(request.referenceType())
                .referenceId(request.referenceId())
                .idempotencyKey(request.idempotencyKey())
                .description(request.description())
                .completedAt(request.completedAt())
                .build();
    }

    public WalletTransactionResponse toResponse(WalletTransaction entity) {
        return new WalletTransactionResponse(
                entity.getTransactionId(), entity.getWalletId(), entity.getType(),
                entity.getDirection(), entity.getAmountVnd(), entity.getBalanceBeforeVnd(),
                entity.getBalanceAfterVnd(), entity.getStatus(), entity.getReferenceType(),
                entity.getReferenceId(), entity.getIdempotencyKey(), entity.getDescription(),
                entity.getCreatedAt(), entity.getCompletedAt());
    }
}

