package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.VoucherRedemptionStatus;
import java.time.Instant;

public record VoucherRedemptionResponse(
        String redemptionId,
        String voucherId,
        String userId,
        String orderId,
        Long discountAmountVnd,
        VoucherRedemptionStatus status,
        Instant redeemedAt,
        Instant cancelledAt
) {}

