package com.scanms.payment.dto.request;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateWalletTransactionRequest(
        @NotNull String walletId,
        @NotNull WalletTransactionType type,
        @NotNull WalletTransactionDirection direction,
        @NotNull @Positive Long amountVnd,
        @NotNull WalletTransactionStatus status,
        String referenceType,
        String referenceId,
        @NotBlank String idempotencyKey,
        String description,
        Instant completedAt
) {}

