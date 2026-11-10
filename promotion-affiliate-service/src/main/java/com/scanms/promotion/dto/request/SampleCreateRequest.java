package com.scanms.promotion.dto.request;

import jakarta.validation.constraints.*;

public record SampleCreateRequest(
        @NotBlank @Size(max = 36) String requestId,
        @NotBlank String productId,
        @NotBlank @Size(max = 2000) String note,
        @NotBlank @Size(max = 255) String recipientName,
        @NotBlank @Pattern(regexp = "[+0-9 ()-]{9,20}") String recipientPhone,
        @NotBlank @Size(max = 2000) String shippingAddress) {
}
