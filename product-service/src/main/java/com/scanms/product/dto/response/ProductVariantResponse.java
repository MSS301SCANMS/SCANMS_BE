package com.scanms.product.dto.response;

import com.scanms.product.constant.ProductVariantStatus;
import java.time.Instant;

public record ProductVariantResponse(
        String variantId,
        String productId,
        String sku,
        String size,
        String color,
        Long priceVnd,
        Integer stockQuantity,
        ProductVariantStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

