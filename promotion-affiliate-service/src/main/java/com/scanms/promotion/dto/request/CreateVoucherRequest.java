package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateVoucherRequest(
        @NotBlank String code,
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
        Map<String, Object> funding
) {}
