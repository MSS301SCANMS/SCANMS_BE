package com.scanms.product.service;

import com.scanms.product.dto.response.OutfitRecommendResponse;
import org.springframework.web.multipart.MultipartFile;

public interface OutfitRecommendService {
    OutfitRecommendResponse recommend(MultipartFile image, int limit);
}
