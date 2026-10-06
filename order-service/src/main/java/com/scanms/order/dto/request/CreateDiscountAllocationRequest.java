package com.scanms.order.dto.request;

import com.scanms.order.constant.*;
import jakarta.validation.constraints.*;

public record CreateDiscountAllocationRequest(
        @NotNull String orderItemId,
        String voucherId,
        @NotNull DiscountSourceType sourceType,
        @NotNull DiscountFundingType fundedBy,
        @NotNull @PositiveOrZero Long allocatedAmountVnd,
        String ruleSnapshotJson
) {}

