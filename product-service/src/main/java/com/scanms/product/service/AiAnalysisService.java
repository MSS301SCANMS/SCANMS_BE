package com.scanms.product.service;

import com.scanms.product.client.AiServiceClient;
import com.scanms.product.dto.ai.AiDetectionResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {
    private final AiServiceClient aiServiceClient;

    public AiDetectionResponse analyze(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Image file is required");
        }

        try {
            return aiServiceClient.analyze(image);
        } catch (FeignException exception) {
            if (exception.status() == 400 || exception.status() == 413 || exception.status() == 422) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "AI service rejected the image");
            }
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
