package com.scanms.product.service.impl;

import com.scanms.product.client.AiServiceClient;
import com.scanms.product.dto.ai.AiDetectionResponse;
import com.scanms.product.dto.ai.ShopAiAnalyzeResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.service.AiAnalysisService;
import com.scanms.product.service.ClothingGarmentMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
        String className = raw.topPrediction() != null ? raw.topPrediction().className() : null;
        if ((className == null || className.isBlank()) && raw.detections() != null && !raw.detections().isEmpty()) {
            className = raw.detections().getFirst().className();
        }
        boolean clothing = ClothingGarmentMapper.isClothing(className);
        return ShopAiAnalyzeResponse.from(
                raw,
                ClothingGarmentMapper.garmentType(className).name(),
                ClothingGarmentMapper.displayLabel(className),
                ClothingGarmentMapper.suggestedCategory(className),
                clothing);
    }
}
