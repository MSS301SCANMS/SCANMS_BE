package com.scanms.payment.dto.response;

import com.scanms.payment.constant.*;
import java.time.Instant;

public record WalletTransactionResponse(
        String transactionId,
        String walletId,
        WalletTransactionType type,
        WalletTransactionDirection direction,
        Long amountVnd,
        Long balanceBeforeVnd,
        Long balanceAfterVnd,
        WalletTransactionStatus status,
        String referenceType,
        String referenceId,
        String idempotencyKey,
        String description,
        Instant createdAt,
        Instant completedAt
) {}

