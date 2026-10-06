package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateCommissionRequest(
        @NotNull String orderItemId,
        @NotNull String collaboratorId,
        String referralLinkId,
        String commissionPolicyId,
        @PositiveOrZero Long basisVnd,
        BigDecimal rate,
        @PositiveOrZero Long amountVnd,
        CommissionStatus status,
        Map<String, Object> adjustmentHistory,
        String transferReference,
        LocalDateTime paidAt
) {}
