package com.scanms.order.dto.request;

import jakarta.validation.constraints.Size;

public record OrderCancellationDecision(boolean approved, @Size(max = 500) String reason) {
}
