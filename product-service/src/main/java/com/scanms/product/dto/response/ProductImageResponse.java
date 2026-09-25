package com.scanms.product.dto.response;

import java.time.Instant;

public record ProductImageResponse(
        String imageId,
        String productId,
        String objectKey,
        String imageUrl,
        boolean primaryImage,
        Integer sortOrder,
        String altText,
        Instant createdAt,
        Instant updatedAt
) {}

