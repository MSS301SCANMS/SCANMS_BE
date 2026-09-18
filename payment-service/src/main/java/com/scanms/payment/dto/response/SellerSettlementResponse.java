package com.scanms.payment.dto.response;

import com.scanms.payment.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record SellerSettlementResponse(
        UUID settlementId,
        UUID storeId,
        UUID bankAccountId,
        Map<String, Object> destinationSnapshot,
        Map<String, Object> orderRefs,
        Map<String, Object> breakdown,
        Long netAmountVnd,
        SettlementStatus status,
        String transferReference,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
