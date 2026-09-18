package com.scanms.payment.dto.response;

import com.scanms.payment.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        String providerCode,
        String transactionId,
        Long amountVnd,
        String currency,
        PaymentStatus status,
        String idempotencyKey,
        LocalDateTime verifiedAt,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
