package com.scanms.payment.dto.response;

import com.scanms.payment.constant.*;
import java.math.BigDecimal;
import java.time.Instant;

public record FeeConfigResponse(
        String feeConfigId,
        FeeType feeType,
        FeeCalculationType calculationType,
        BigDecimal ratePercent,
        Long fixedAmountVnd,
        Long minFeeVnd,
        Long maxFeeVnd,
        Instant validFrom,
        Instant validTo,
        FeeConfigStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

