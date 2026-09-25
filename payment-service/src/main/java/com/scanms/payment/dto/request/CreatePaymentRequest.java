package com.scanms.payment.dto.request;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreatePaymentRequest(
        @NotNull String orderId,
        @NotBlank String providerCode,
        String transactionId,
        @PositiveOrZero Long amountVnd,
        @NotBlank String currency,
        PaymentStatus status,
        @NotBlank String idempotencyKey,
        LocalDateTime verifiedAt,
        String failureReason
) {}
