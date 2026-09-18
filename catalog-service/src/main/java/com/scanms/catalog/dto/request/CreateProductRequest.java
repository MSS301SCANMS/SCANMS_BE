package com.scanms.catalog.dto.request;

import com.scanms.catalog.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateProductRequest(
        @NotNull UUID storeId,
        @NotBlank String name,
        String description,
        String category,
        String material,
        Map<String, Object> sizesStock,
        Map<String, Object> sizeGuide,
        Map<String, Object> imageRefs,
        @PositiveOrZero Long priceVnd,
        ProductStatus status
) {}
