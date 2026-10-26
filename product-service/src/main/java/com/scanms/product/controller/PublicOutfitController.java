package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.response.OutfitRecommendResponse;
import com.scanms.product.service.OutfitRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Customer/public outfit matching: detect clothing from an uploaded photo,
 * then recommend complementary shoes and hats from the live catalog.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicOutfitController {
    private final OutfitRecommendService outfitRecommendService;

    @PostMapping(value = "/outfit-recommend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OutfitRecommendResponse> recommend(
            @RequestPart("image") MultipartFile image,
            @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.success(outfitRecommendService.recommend(image, limit));
    }
}
