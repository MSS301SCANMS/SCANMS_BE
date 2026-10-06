package com.scanms.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

public record CheckoutItemRequest(
        @NotBlank String productId,
        String variantId,
        String storeId,
        String productTitle,
        String sku,
        String imageUrl,
        String selectedSize,
        @NotNull @Positive Integer quantity,
        @NotNull @PositiveOrZero Long unitPrice,
        String referralLinkId,
        String sourceLivestreamId,
        Map<String, Object> attributes
) {}
