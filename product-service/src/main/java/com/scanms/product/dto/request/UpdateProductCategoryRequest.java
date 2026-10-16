package com.scanms.product.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductCategoryRequest(String name, String slug, String description,
        Boolean active, @PositiveOrZero Integer displayOrder) {}
