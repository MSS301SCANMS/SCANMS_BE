package com.scanms.payment.dto.response;

import com.scanms.payment.constant.WithdrawalStatus;
import java.time.Instant;

public record WithdrawalRequestResponse(
        String withdrawalId,
        String walletId,
        String bankAccountId,
        Long amountVnd,
        WithdrawalStatus status,
        String providerReference,
        String failureReason,
        Instant requestedAt,
        Instant approvedAt,
        Instant processedAt,
        Instant completedAt
) {}

