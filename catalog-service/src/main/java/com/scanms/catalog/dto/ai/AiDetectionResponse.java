package com.scanms.catalog.dto.ai;

import java.util.List;

public record AiDetectionResponse(
        List<AiDetectionItem> detections,
        AiTopPrediction topPrediction,
        double inferenceTimeMs) {
}
