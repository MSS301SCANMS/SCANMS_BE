package com.scanms.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderCancellationRequest(@NotBlank @Size(max = 500) String reason) {
}
