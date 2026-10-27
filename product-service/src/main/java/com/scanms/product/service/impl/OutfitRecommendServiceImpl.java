package com.scanms.product.service.impl;

import com.scanms.product.dto.ai.AiDominantColor;
import com.scanms.product.dto.ai.ShopAiAnalyzeResponse;
import com.scanms.product.dto.response.OutfitProductCard;
import com.scanms.product.dto.response.OutfitRecommendResponse;
import com.scanms.product.service.AiAnalysisService;
import com.scanms.product.service.OutfitRecommendService;
import com.scanms.product.service.PublicCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutfitRecommendServiceImpl implements OutfitRecommendService {
    private static final Set<String> SHOE_TOKENS = Set.of(
            "giay", "giày", "sneaker", "dep", "dép", "sandal", "boot", "footwear", "loafer", "shoes", "shoe");
    private static final Set<String> HAT_TOKENS = Set.of(
            "non", "nón", "mu", "mũ", "hat", "cap", "beanie", "bucket", "beret", "fedora");

    private final AiAnalysisService aiAnalysis;
    private final PublicCatalogService catalog;

    @Override
    public OutfitRecommendResponse recommend(MultipartFile image, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 12);
        ShopAiAnalyzeResponse detection = aiAnalysis.analyze(image);
        if (!detection.clothingDetected()) {
            return new OutfitRecommendResponse(
                    detection,
                    List.of(),
                    List.of(),
                    "Ảnh chưa nhận diện được quần/áo. Hãy tải ảnh trang phục rõ hơn.");
        }

        List<Map<String, Object>> products = catalog.visibleProducts();
        String style = detection.stylePrediction() != null ? detection.stylePrediction().style() : null;
        List<String> colors = detection.dominantColors() == null
                ? List.of()
                : detection.dominantColors().stream().map(AiDominantColor::name).filter(Objects::nonNull).toList();

        List<OutfitProductCard> shoes = rank(products, SHOE_TOKENS, colors, style, "Phối giày", safeLimit);
        List<OutfitProductCard> hats = rank(products, HAT_TOKENS, colors, style, "Phối nón", safeLimit);

        String message;
        if (shoes.isEmpty() && hats.isEmpty()) {
            message = "Đã nhận diện " + detection.displayLabel()
                    + " nhưng chưa có sản phẩm giày/nón phù hợp. Thử lại sau khi sàn có thêm phụ kiện.";
        } else {
            message = "Đã nhận diện " + detection.displayLabel()
                    + (style != null ? " · style " + style : "")
                    + ". Gợi ý giày và nón từ sản phẩm đang bán trên SCANMS.";
        }
        return new OutfitRecommendResponse(detection, shoes, hats, message);
    }

    private List<OutfitProductCard> rank(
            List<Map<String, Object>> products,
            Set<String> accessoryTokens,
            List<String> colors,
            String style,
            String reasonPrefix,
            int limit) {
        return products.stream()
                .map(p -> score(p, accessoryTokens, colors, style, reasonPrefix))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(OutfitProductCard::matchScore).reversed())
                .limit(limit)
                .toList();
    }

    private OutfitProductCard score(
            Map<String, Object> product,
            Set<String> accessoryTokens,
            List<String> colors,
            String style,
            String reasonPrefix) {
        String haystack = normalize(String.join(" ",
                str(product.get("title")),
                str(product.get("name")),
                str(product.get("description")),
                str(product.get("category")),
                str(product.get("categoryName")),
                attributesText(product.get("attributes"))));
        if (haystack.isBlank()) return null;
        boolean accessoryHit = accessoryTokens.stream().anyMatch(token -> haystack.contains(normalize(token)));
        if (!accessoryHit) return null;

        double score = 0.45;
        StringBuilder reason = new StringBuilder(reasonPrefix);
        for (String color : colors) {
            if (color != null && !color.isBlank() && haystack.contains(normalize(color))) {
                score += 0.25;
                reason.append(" · màu ").append(color);
                break;
            }
        }
        if (style != null && !style.isBlank() && haystack.contains(normalize(style))) {
            score += 0.2;
            reason.append(" · style ").append(style);
        }
        if (haystack.contains("thoi trang") || haystack.contains("fashion") || haystack.contains("phu kien")) {
            score += 0.05;
        }
        Object store = product.get("store");
        String storeName = "";
        if (store instanceof Map<?, ?> map) storeName = str(map.get("name"));
        return new OutfitProductCard(
                str(product.get("productId") != null ? product.get("productId") : product.get("id")),
                str(product.get("title") != null ? product.get("title") : product.get("name")),
                str(product.get("imageUrl")),
                toLong(product.get("priceVnd") != null ? product.get("priceVnd") : product.get("price")),
                str(product.get("storeId")),
                storeName,
                str(product.get("categoryName") != null ? product.get("categoryName") : product.get("category")),
                reason.toString(),
                Math.min(1.0, score));
    }

    private static String attributesText(Object attributes) {
        if (!(attributes instanceof Map<?, ?> map) || map.isEmpty()) return "";
        return map.values().stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(" "));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String nfd = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return nfd.toLowerCase(Locale.ROOT).replace('đ', 'd');
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
