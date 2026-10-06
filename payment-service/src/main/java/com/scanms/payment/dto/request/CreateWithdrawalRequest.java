package com.scanms.payment.dto.request;

import com.scanms.payment.constant.WithdrawalStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateWithdrawalRequest(
        @NotNull String walletId,
        @NotNull String bankAccountId,
        @NotNull @Positive Long amountVnd,
        @NotNull WithdrawalStatus status,
        String providerReference,
        String failureReason,
        Instant approvedAt,
        Instant processedAt,
        Instant completedAt
) {}
