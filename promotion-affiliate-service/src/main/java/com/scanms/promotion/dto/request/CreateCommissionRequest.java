package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateCommissionRequest(
        @NotNull UUID orderItemId,
        @NotNull UUID collaboratorId,
        UUID referralLinkId,
        @PositiveOrZero Long basisVnd,
        BigDecimal rate,
        @PositiveOrZero Long amountVnd,
        CommissionStatus status,
        Map<String, Object> adjustmentHistory,
        String transferReference,
        LocalDateTime paidAt
) {}
