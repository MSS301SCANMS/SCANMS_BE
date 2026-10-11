package com.scanms.product.dto.request;

import com.scanms.product.constant.ProductVariantStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductVariantRequest(String size, String color,
        @Positive Long priceVnd, @PositiveOrZero Integer stockQuantity,
        ProductVariantStatus status) {}
