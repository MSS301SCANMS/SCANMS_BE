package com.scanms.catalog.dto.ai;

public record AiDetectionItem(
        int classId,
        String className,
        double confidence,
        AiBoundingBox boundingBox) {
}
