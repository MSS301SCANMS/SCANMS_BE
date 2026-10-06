package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

public record CreateProductImageRequest(
        @NotNull String productId,
        @NotBlank String objectKey,
        @NotBlank String imageUrl,
        boolean primaryImage,
        @PositiveOrZero Integer sortOrder,
        String altText
) {}

