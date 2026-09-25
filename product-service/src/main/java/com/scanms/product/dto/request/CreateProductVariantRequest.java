package com.scanms.product.dto.request;

import com.scanms.product.constant.ProductVariantStatus;
import jakarta.validation.constraints.*;

public record CreateProductVariantRequest(
        @NotNull String productId,
        @NotBlank String sku,
        String size,
        String color,
        @NotNull @PositiveOrZero Long priceVnd,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @NotNull ProductVariantStatus status
) {}

