package com.scanms.product.dto.ai;

public record AiDetectionItem(
        int classId,
        String className,
        double confidence,
        AiBoundingBox boundingBox) {
}
