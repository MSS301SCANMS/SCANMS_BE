package com.scanms.catalog.dto.request;

import com.scanms.catalog.constant.ProductStatus;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

public record UpdateProductRequest(String name, String description, String category, String material,
        Map<String, Object> sizesStock, Map<String, Object> sizeGuide, Map<String, Object> imageRefs,
        @PositiveOrZero Long priceVnd, ProductStatus status) {}
