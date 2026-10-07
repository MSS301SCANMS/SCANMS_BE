package com.scanms.payment.dto;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class MoneyDtos {
    private MoneyDtos() {}
    public record WalletView(String walletId, WalletOwnerType ownerType, String ownerRefId, String currency,
                             long availableBalanceVnd, long heldBalanceVnd, WalletStatus status) {}
    public record LedgerView(String transactionId, WalletTransactionType type, WalletTransactionDirection direction,
            long amountVnd, long balanceBeforeVnd, long balanceAfterVnd, Long heldBeforeVnd, Long heldAfterVnd,
            String referenceType, String referenceId, Instant createdAt) {}
    public record BankInput(@NotNull WalletOwnerType ownerType, @NotBlank String ownerRefId,
            @NotBlank @Pattern(regexp="[0-9]{6}") String bankCode, @NotBlank @Size(max=100) String holderName,
            @NotBlank @Pattern(regexp="[0-9]{6,30}") String accountNumber) {}
    public record BankView(String bankAccountId, String storeId, String collaboratorId, String bankCode,
                           String holderName, String maskedNumber, BankAccountVerificationStatus verificationStatus, Boolean active, String sourceKycReference, String replacesBankId) {}
    public record KycBankInput(@NotNull @jakarta.validation.Valid BankInput bank,@NotBlank @Size(max=120) String caseReference,boolean bankOwnershipVerified) {}
    public record WithdrawalInput(@NotBlank String walletId, @NotBlank String bankAccountId,
            @NotNull @Positive Long amountVnd, @NotBlank @Size(max=128) String idempotencyKey) {}
    public record WithdrawalView(String withdrawalId, String walletId, String bankAccountId, long amountVnd,
            Long feeVnd, Long netAmountVnd, WithdrawalStatus status, String providerReference, String failureReason,
            Instant requestedAt, Instant approvedAt, Instant scheduledFor, Instant completedAt) {}
    public record Decision(@Size(max=500) String reason) {}
    public record PaymentInput(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") String orderId, @NotBlank @Size(max=128) String idempotencyKey) {}
    public record TopUpInput(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") String walletId, @NotNull @Positive Long amountVnd, @NotBlank @Size(max=128) String idempotencyKey) {}
    public record WalletPayInput(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") String orderId, @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") String walletId, @NotBlank @Size(max=128) String idempotencyKey) {}
    public record PaymentView(String paymentId, String orderId, String purpose, Long amountVnd, String currency,
            PaymentStatus status, String checkoutUrl, String qrCode, Long providerOrderCode, String failureReason,
            LocalDateTime verifiedAt, LocalDateTime expiresAt, String orderSyncStatus, String resolutionReference,
            Long receivedAmountVnd, Long resolutionAmountVnd, Long surplusRefundedAmountVnd, String surplusReference,
            String bin, String accountNumber, String accountName, String description) {}
    public record SettlementInput(@NotBlank @Pattern(regexp="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}") String sellerOrderId) {}
    public record SettlementView(String settlementId, String sellerOrderId, String storeId, Long netAmountVnd,
            SettlementStatus status, Map<String,Object> breakdown, String walletTransactionId, LocalDateTime paidAt) {}
    public record Verification(@NotNull BankAccountVerificationStatus status) {}
    public record PageView<T>(List<T> items, long total, int page, int size) {}
}
