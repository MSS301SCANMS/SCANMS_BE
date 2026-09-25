package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

public record CreateProductCategoryRequest(
        String parentId,
        @NotBlank String name,
        @NotBlank String slug,
        String description,
        boolean active,
        @PositiveOrZero Integer displayOrder
) {}

