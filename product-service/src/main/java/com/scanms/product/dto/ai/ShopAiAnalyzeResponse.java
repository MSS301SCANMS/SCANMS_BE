package com.scanms.product.dto.ai;

import java.util.List;

/** Shop-facing AI analyze payload: raw detections plus garment mapping for form autofill. */
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
        boolean clothingDetected) {

    public static ShopAiAnalyzeResponse from(AiDetectionResponse raw, String garmentType, String displayLabel,
                                             String suggestedCategory, boolean clothingDetected) {
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
                clothingDetected);
    }
}
