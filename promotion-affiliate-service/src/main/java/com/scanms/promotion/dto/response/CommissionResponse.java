package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CommissionResponse(
        String commissionId,
        String orderItemId,
        String collaboratorId,
        String referralLinkId,
        String commissionPolicyId,
        Long basisVnd,
        BigDecimal rate,
        Long amountVnd,
        CommissionStatus status,
        Map<String, Object> adjustmentHistory,
        String transferReference,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
