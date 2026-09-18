package com.scanms.catalog.dto.response;

import com.scanms.catalog.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ProductResponse(
        UUID productId,
        UUID storeId,
        String name,
        String description,
        String category,
        String material,
        Map<String, Object> sizesStock,
        Map<String, Object> sizeGuide,
        Map<String, Object> imageRefs,
        Long priceVnd,
        ProductStatus status,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
