package com.scanms.payment.dto.request;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateFeeConfigRequest(
        @NotNull FeeType feeType,
        @NotNull FeeCalculationType calculationType,
        @PositiveOrZero @Digits(integer=3,fraction=6) BigDecimal ratePercent,
        @PositiveOrZero Long fixedAmountVnd,
        @PositiveOrZero Long minFeeVnd,
        @PositiveOrZero Long maxFeeVnd,
        @NotNull Instant validFrom,
        Instant validTo,
        @NotNull FeeConfigStatus status
) {}

