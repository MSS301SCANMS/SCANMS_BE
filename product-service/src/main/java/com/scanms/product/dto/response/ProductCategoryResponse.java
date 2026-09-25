package com.scanms.product.dto.response;

import java.time.Instant;

public record ProductCategoryResponse(
        String categoryId,
        String parentId,
        String name,
        String slug,
        String description,
        boolean active,
        Integer displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}

