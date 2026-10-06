package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.VoucherRedemptionStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateVoucherRedemptionRequest(
        @NotNull String voucherId,
        @NotNull String userId,
        @NotNull String orderId,
        @NotNull @PositiveOrZero Long discountAmountVnd,
        @NotNull VoucherRedemptionStatus status,
        Instant cancelledAt
) {}

