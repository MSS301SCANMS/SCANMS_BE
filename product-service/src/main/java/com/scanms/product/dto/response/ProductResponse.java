package com.scanms.product.dto.response;

import com.scanms.product.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ProductResponse(
        String productId,
        String storeId,
        String categoryId,
        String name,
        String description,
        String category,
        String material,
        Map<String, Object> attributes,
        Map<String, Object> sizesStock,
        Map<String, Object> sizeGuide,
        Map<String, Object> imageRefs,
        Long priceVnd,
        ProductStatus status,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
