package com.scanms.product.dto.ai;

public record DetectedGarment(
        String className,
        String displayLabel,
        String garmentType,
        double confidence) {
}
