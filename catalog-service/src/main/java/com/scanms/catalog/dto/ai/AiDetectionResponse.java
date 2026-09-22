package com.scanms.catalog.dto.ai;

import java.util.List;

public record AiDetectionResponse(
        List<AiDetectionItem> detections,
        AiTopPrediction topPrediction,
        List<AiDominantColor> dominantColors,
        AiStylePrediction stylePrediction,
        boolean detectionFallback,
        boolean embeddingGenerated,
        double inferenceTimeMs) {
}
