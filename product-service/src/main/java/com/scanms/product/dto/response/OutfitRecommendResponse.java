package com.scanms.product.dto.response;

import com.scanms.product.dto.ai.ShopAiAnalyzeResponse;

import java.util.List;

public record OutfitRecommendResponse(
        ShopAiAnalyzeResponse detection,
        List<OutfitProductCard> shoes,
        List<OutfitProductCard> hats,
        String message) {
}
