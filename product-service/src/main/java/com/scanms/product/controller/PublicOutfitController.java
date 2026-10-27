package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.response.OutfitRecommendResponse;
import com.scanms.product.service.OutfitRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

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
            @RequestParam(defaultValue = "6") int limit,
            @RequestParam(required = false) String preferredGenderTarget,
            @RequestParam(required = false) String preferredStyles,
            @RequestParam(required = false) String preferredColors) {
        return ApiResponse.success(outfitRecommendService.recommend(
                image,
                limit,
                preferredGenderTarget,
                splitCsv(preferredStyles),
                splitCsv(preferredColors)));
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,;|]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
