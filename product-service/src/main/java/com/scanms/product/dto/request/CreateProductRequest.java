package com.scanms.product.dto.request;

import com.scanms.product.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateProductRequest(
        @NotNull String storeId,
        String categoryId,
        @NotBlank String name,
        String description,
        String category,
        String material,
        Map<String, Object> attributes,
        Map<String, Object> sizesStock,
        Map<String, Object> sizeGuide,
        Map<String, Object> imageRefs,
        @PositiveOrZero Long priceVnd,
        ProductStatus status
) {}
