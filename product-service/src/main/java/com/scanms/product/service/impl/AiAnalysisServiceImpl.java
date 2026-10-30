package com.scanms.product.service.impl;

import com.scanms.product.client.AiServiceClient;
import com.scanms.product.dto.ai.*;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.service.AiAnalysisService;
import com.scanms.product.service.ClothingGarmentMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {
    private final AiServiceClient aiServiceClient;

    @Override
    public ShopAiAnalyzeResponse analyze(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Image file is required");
        }

        try {
            AiDetectionResponse raw = aiServiceClient.analyze(image);
            return enrich(raw);
        } catch (FeignException exception) {
            if (exception.status() == 400 || exception.status() == 413 || exception.status() == 422) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "AI service rejected the image");
            }
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    public static ShopAiAnalyzeResponse enrich(AiDetectionResponse raw) {
        List<DetectedGarment> garments = new ArrayList<>();
        if (raw.detections() != null) {
            for (AiDetectionItem item : raw.detections()) {
                if (item == null || !ClothingGarmentMapper.isClothing(item.className())) continue;
                garments.add(new DetectedGarment(
                        item.className(),
                        ClothingGarmentMapper.displayLabel(item.className()),
                        ClothingGarmentMapper.garmentType(item.className()).name(),
                        item.confidence()));
            }
        }

        boolean clothing = !garments.isEmpty();
        if (!clothing) {
            String className = raw.topPrediction() != null ? raw.topPrediction().className() : null;
            return ShopAiAnalyzeResponse.from(
                    raw,
                    ClothingGarmentMapper.garmentType(className).name(),
                    ClothingGarmentMapper.displayLabel(className),
                    ClothingGarmentMapper.suggestedCategory(className),
                    false,
                    List.of());
        }

        Set<String> types = new LinkedHashSet<>();
        List<String> labels = new ArrayList<>();
        for (DetectedGarment garment : garments) {
            types.add(garment.garmentType());
            if (!labels.contains(garment.displayLabel())) labels.add(garment.displayLabel());
        }
        String garmentType = String.join("+", types);
        String displayLabel = String.join(" · ", labels);
        return ShopAiAnalyzeResponse.from(
                raw,
                garmentType,
                displayLabel,
                ClothingGarmentMapper.SUGGESTED_CATEGORY,
                true,
                List.copyOf(garments));
    }
}
