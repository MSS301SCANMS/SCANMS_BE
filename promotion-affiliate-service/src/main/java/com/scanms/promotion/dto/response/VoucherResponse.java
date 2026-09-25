package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record VoucherResponse(
        String voucherId,
        String code,
        VoucherIssuerType issuerType,
        String storeId,
        String productId,
        String livestreamId,
        VoucherScopeType scopeType,
        VoucherDiscountType discountType,
        BigDecimal value,
        Long cap,
        Long minimumSubtotal,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Map<String, Object> limits,
        Map<String, Object> scope,
        Map<String, Object> funding,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
