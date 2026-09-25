package com.scanms.payment.dto.request;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateSellerSettlementRequest(
        @NotNull String storeId,
        @NotNull String bankAccountId,
        String walletId,
        String walletTransactionId,
        Map<String, Object> destinationSnapshot,
        Map<String, Object> orderRefs,
        Map<String, Object> breakdown,
        @PositiveOrZero Long netAmountVnd,
        SettlementStatus status,
        String transferReference,
        LocalDateTime paidAt
) {}
