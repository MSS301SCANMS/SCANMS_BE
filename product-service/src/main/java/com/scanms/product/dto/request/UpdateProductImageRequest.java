package com.scanms.product.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductImageRequest(String objectKey, String imageUrl, Boolean primaryImage,
        @PositiveOrZero Integer sortOrder, String altText) {}
