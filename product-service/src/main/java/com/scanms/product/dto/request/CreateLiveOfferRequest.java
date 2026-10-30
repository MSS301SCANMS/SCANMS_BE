package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateLiveOfferRequest(
        @NotBlank String productId,
        @NotBlank String variantId,
        @NotNull @Positive Long priceVnd,
        @NotNull @Min(1) @Max(100000) Long quantityLimit,
        Instant startsAt,
        Instant endsAt
) {
}
