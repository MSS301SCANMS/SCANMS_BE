package com.scanms.product.service;

import com.scanms.product.dto.ai.AiDetectionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AiAnalysisService {
    AiDetectionResponse analyze(MultipartFile image);
}
