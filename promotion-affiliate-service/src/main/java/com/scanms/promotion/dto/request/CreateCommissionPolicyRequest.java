package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateCommissionPolicyRequest(
        @NotBlank String policyName,
        @NotNull CommissionPolicyScope scopeType,
        String storeId,
        String productId,
        String livestreamId,
        String collaboratorId,
        @NotNull @DecimalMin("0.0") BigDecimal commissionRate,
        @NotNull @PositiveOrZero Integer returnWindowDays,
        @NotNull Instant validFrom,
        Instant validTo,
        @NotNull CommissionPolicyStatus status,
        @NotNull @PositiveOrZero Integer priority
) {}

