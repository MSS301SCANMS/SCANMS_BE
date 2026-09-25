package com.scanms.order.dto.response;

import com.scanms.order.constant.*;
import java.time.Instant;

public record DiscountAllocationResponse(
        String allocationId,
        String orderItemId,
        String voucherId,
        DiscountSourceType sourceType,
        DiscountFundingType fundedBy,
        Long allocatedAmountVnd,
        String ruleSnapshotJson,
        Instant createdAt
) {}

