package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
import java.time.Instant;

public record CommissionPolicyResponse(
        String policyId,
        String policyName,
        CommissionPolicyScope scopeType,
        String storeId,
        String productId,
        String livestreamId,
        String collaboratorId,
        BigDecimal commissionRate,
        Integer returnWindowDays,
        Instant validFrom,
        Instant validTo,
        CommissionPolicyStatus status,
        Integer priority,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

