package com.scanms.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreChatOpenRequest(
        @NotBlank String storeId,
        @Size(max = 255) String customerId) {
}
