package com.scanms.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderCancellationRefund(@NotBlank String paymentId, @NotBlank String transactionId,
                                      @NotNull @Min(1) Long amountVnd) {
}
