package com.scanms.product.service;

import com.scanms.product.dto.response.OutfitRecommendResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OutfitRecommendService {
    OutfitRecommendResponse recommend(
            MultipartFile image,
            int limit,
            String preferredGenderTarget,
            List<String> preferredStyles,
            List<String> preferredColors);
}
