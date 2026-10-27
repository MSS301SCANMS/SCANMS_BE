package com.scanms.product.dto.ai;

import java.util.List;

/** Shop/customer-facing AI analyze payload: all garment detections plus summary hints. */
public record ShopAiAnalyzeResponse(
        List<AiDetectionItem> detections,
        AiTopPrediction topPrediction,
        List<AiDominantColor> dominantColors,
        AiStylePrediction stylePrediction,
        boolean detectionFallback,
        boolean embeddingGenerated,
        double inferenceTimeMs,
        String garmentType,
        String displayLabel,
        String suggestedCategory,
        boolean clothingDetected,
        List<DetectedGarment> detectedGarments) {

    public static ShopAiAnalyzeResponse from(
            AiDetectionResponse raw,
            String garmentType,
            String displayLabel,
            String suggestedCategory,
            boolean clothingDetected,
            List<DetectedGarment> detectedGarments) {
        return new ShopAiAnalyzeResponse(
                raw.detections(),
                raw.topPrediction(),
                raw.dominantColors(),
                raw.stylePrediction(),
                raw.detectionFallback(),
                raw.embeddingGenerated(),
                raw.inferenceTimeMs(),
                garmentType,
                displayLabel,
                suggestedCategory,
                clothingDetected,
                detectedGarments);
    }
}
