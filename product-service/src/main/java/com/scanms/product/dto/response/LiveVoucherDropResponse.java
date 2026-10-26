package com.scanms.product.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record LiveVoucherDropResponse(
        String id, String voucherId, String code, String issuerType, String benefitType,
        String discountType, BigDecimal value, Long cap, Long minimumSubtotal,
        Long quantity, Instant releaseAt, Instant expiresAt, String status
) {}
