package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateWithdrawalRequest;
import com.scanms.payment.dto.response.WithdrawalRequestResponse;
import com.scanms.payment.entity.WithdrawalRequest;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalRequestMapper {
    public WithdrawalRequest toEntity(CreateWithdrawalRequest request) {
        return WithdrawalRequest.builder()
                .walletId(request.walletId())
                .bankAccountId(request.bankAccountId())
                .amountVnd(request.amountVnd())
                .status(request.status())
                .providerReference(request.providerReference())
                .failureReason(request.failureReason())
                .approvedAt(request.approvedAt())
                .processedAt(request.processedAt())
                .completedAt(request.completedAt())
                .build();
    }
    public WithdrawalRequestResponse toResponse(WithdrawalRequest entity) {
        return new WithdrawalRequestResponse(
                entity.getWithdrawalId(),
                entity.getWalletId(),
                entity.getBankAccountId(),
                entity.getAmountVnd(),
                entity.getStatus(),
                entity.getProviderReference(),
                entity.getFailureReason(),
                entity.getRequestedAt(),
                entity.getApprovedAt(),
                entity.getProcessedAt(),
                entity.getCompletedAt());
    }
}
