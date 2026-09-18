package com.scanms.catalog.service;

import com.scanms.catalog.client.AiServiceClient;
import com.scanms.catalog.dto.ai.AiDetectionResponse;
import com.scanms.catalog.exception.AppException;
import com.scanms.catalog.exception.ErrorCode;
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
            return aiServiceClient.detect(image);
        } catch (FeignException.BadRequest exception) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "AI service rejected the image");
        } catch (FeignException exception) {
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
