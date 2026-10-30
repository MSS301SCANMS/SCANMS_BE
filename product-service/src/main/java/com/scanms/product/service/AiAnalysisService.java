package com.scanms.product.service;

import com.scanms.product.dto.ai.ShopAiAnalyzeResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AiAnalysisService {
    ShopAiAnalyzeResponse analyze(MultipartFile image);
}
