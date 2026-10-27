package com.scanms.product.service.impl;

import com.scanms.product.constant.FashionTags;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutfitRecommendServiceImpl implements OutfitRecommendService {
    private static final Set<String> SHOE_TOKENS = Set.of(
            "giay", "sneaker", "sandal", "boot", "boots", "footwear", "loafer", "loafers",
            "shoes", "shoe", "dep", "guoc", "heels", "heel", "cleat", "cleats");
    private static final Set<String> HAT_TOKENS = Set.of(
            "non", "mu", "hat", "hats", "cap", "caps", "beanie", "bucket", "beret", "fedora",
            "visor", "snapback", "trucker");
    private static final Set<String> APPAREL_TOKENS = Set.of(
            "ao", "quan", "dam", "vay", "corset", "hoodie", "jacket", "shirt", "dress", "skirt",
            "shorts", "jean", "jeans", "blazer", "coat", "khoac", "cardigan", "sweater",
            "set", "bo", "outfit", "trangphuc");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-z0-9]+");

    private final AiAnalysisService aiAnalysis;
    private final PublicCatalogService catalog;

    @Override
    public OutfitRecommendResponse recommend(
            MultipartFile image,
            int limit,
            String preferredGenderTarget,
            List<String> preferredStyles,
            List<String> preferredColors) {
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

        String genderPref = FashionTags.normalizeGenderTarget(preferredGenderTarget);
        List<String> stylePrefs = preferredStyles == null ? List.of() : preferredStyles.stream()
                .map(FashionTags::normalizeFashionStyle).filter(Objects::nonNull).toList();
        List<String> colorPrefs = preferredColors == null ? List.of() : preferredColors.stream()
                .filter(Objects::nonNull)
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isBlank())
                .toList();

        PreferenceContext prefs = new PreferenceContext(genderPref, stylePrefs, colorPrefs, style, colors);

        List<OutfitProductCard> shoes = rank(products, AccessoryKind.SHOE, prefs, "Phối giày", safeLimit);
        Set<String> shoeIds = shoes.stream().map(OutfitProductCard::productId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<OutfitProductCard> hats = rank(products, AccessoryKind.HAT, prefs, "Phối nón", safeLimit).stream()
                .filter(card -> card.productId() == null || !shoeIds.contains(card.productId()))
                .limit(safeLimit)
                .toList();

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
            AccessoryKind kind,
            PreferenceContext prefs,
            String reasonPrefix,
            int limit) {
        return products.stream()
                .map(p -> score(p, kind, prefs, reasonPrefix))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(OutfitProductCard::matchScore).reversed())
                .limit(limit)
                .toList();
    }

    private OutfitProductCard score(
            Map<String, Object> product,
            AccessoryKind kind,
            PreferenceContext prefs,
            String reasonPrefix) {
        String title = str(product.get("title") != null ? product.get("title") : product.get("name"));
        String category = str(product.get("categoryName") != null ? product.get("categoryName") : product.get("category"));
        Map<String, Object> attributes = attributesMap(product.get("attributes"));
        String taggedType = FashionTags.normalizeApparelType(attributes.get(FashionTags.ATTR_APPAREL_TYPE));
        if (taggedType == null) {
            taggedType = FashionTags.inferApparelType(title, category);
        }
        String taggedStyle = FashionTags.normalizeFashionStyle(attributes.get(FashionTags.ATTR_FASHION_STYLE));
        if (taggedStyle == null) {
            taggedStyle = FashionTags.inferFashionStyle(title, category, str(product.get("description")));
        }
        String taggedGender = FashionTags.normalizeGenderTarget(attributes.get(FashionTags.ATTR_GENDER_TARGET));
        if (taggedGender == null) {
            taggedGender = FashionTags.inferGenderTarget(title, category);
        }
        List<String> taggedColors = FashionTags.normalizeColorTags(attributes.get(FashionTags.ATTR_COLOR_TAGS));

        String primary = normalize(String.join(" ", title, category));
        String haystack = normalize(String.join(" ",
                title,
                str(product.get("name")),
                str(product.get("description")),
                category,
                attributesText(attributes)));
        if (primary.isBlank() && haystack.isBlank()) return null;

        Set<String> primaryWords = words(primary);
        Set<String> allWords = words(haystack);
        boolean apparelHit = hitsTokens(primaryWords, APPAREL_TOKENS);

        boolean typeMatch = kind == AccessoryKind.SHOE ? "SHOE".equals(taggedType) : "HAT".equals(taggedType);
        if (!typeMatch) {
            // Fallback keyword only when tag missing/OTHER
            if (!"OTHER".equals(taggedType) && taggedType != null) return null;
            boolean shoeHit = hitsTokens(primaryWords, SHOE_TOKENS) || hitsTokens(allWords, SHOE_TOKENS);
            boolean hatWordHit = hitsTokens(primaryWords, HAT_TOKENS);
            boolean hoodedApparel = apparelHit && (hatWordHit || haystack.contains("co mu"));
            if (kind == AccessoryKind.SHOE) {
                if (!shoeHit) return null;
                if (apparelHit && !hitsTokens(primaryWords, SHOE_TOKENS)) return null;
            } else {
                if (!hatWordHit || apparelHit || hoodedApparel) return null;
                if (shoeHit || looksLikeHoodNotHat(primary, haystack)) return null;
            }
        } else if (kind == AccessoryKind.HAT && apparelHit && !"HAT".equals(taggedType)) {
            return null;
        }

        // Gender filter: keep UNISEX always; drop hard mismatches vs preference
        if (prefs.genderTarget() != null
                && !"UNISEX".equals(taggedGender)
                && !prefs.genderTarget().equals(taggedGender)
                && !"UNISEX".equals(prefs.genderTarget())) {
            return null;
        }

        double score = 0.45;
        StringBuilder reason = new StringBuilder(reasonPrefix);
        if (typeMatch) {
            score += 0.2;
            reason.append(" · ").append(kind == AccessoryKind.SHOE ? "tag giày" : "tag nón");
        }
        if (prefs.detectionStyle() != null && prefs.detectionStyle().equalsIgnoreCase(taggedStyle)) {
            score += 0.18;
            reason.append(" · style ").append(taggedStyle);
        } else if (prefs.stylePrefs().contains(taggedStyle)) {
            score += 0.16;
            reason.append(" · sở thích ").append(taggedStyle);
        } else if (prefs.detectionStyle() != null && haystack.contains(normalize(prefs.detectionStyle()))) {
            score += 0.1;
            reason.append(" · style ").append(prefs.detectionStyle());
        }

        boolean colorHit = false;
        for (String color : prefs.detectionColors()) {
            if (color != null && (taggedColors.contains(normalize(color)) || allWords.contains(normalize(color)))) {
                score += 0.2;
                reason.append(" · màu ").append(color);
                colorHit = true;
                break;
            }
        }
        if (!colorHit) {
            for (String color : prefs.colorPrefs()) {
                if (taggedColors.contains(color) || allWords.contains(color)) {
                    score += 0.14;
                    reason.append(" · màu ưa thích ").append(color);
                    break;
                }
            }
        }
        if (prefs.genderTarget() != null && (prefs.genderTarget().equals(taggedGender) || "UNISEX".equals(taggedGender))) {
            score += 0.08;
        }
        if (kind == AccessoryKind.SHOE && prefs.detectionStyle() != null
                && normalize(prefs.detectionStyle()).contains("streetwear")
                && (allWords.contains("cao") && allWords.contains("got")
                || allWords.contains("mui") && allWords.contains("nhon"))) {
            score -= 0.35;
        }

        Object store = product.get("store");
        String storeName = "";
        if (store instanceof Map<?, ?> map) storeName = str(map.get("name"));
        return new OutfitProductCard(
                str(product.get("productId") != null ? product.get("productId") : product.get("id")),
                title,
                str(product.get("imageUrl")),
                toLong(product.get("priceVnd") != null ? product.get("priceVnd") : product.get("price")),
                str(product.get("storeId")),
                storeName,
                category,
                reason.toString(),
                Math.min(1.0, Math.max(0.05, score)));
    }

    private enum AccessoryKind { SHOE, HAT }

    private record PreferenceContext(
            String genderTarget,
            List<String> stylePrefs,
            List<String> colorPrefs,
            String detectionStyle,
            List<String> detectionColors) {}

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributesMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) out.put(String.valueOf(k), v);
            });
            return out;
        }
        return Map.of();
    }

    private static boolean looksLikeHoodNotHat(String primary, String haystack) {
        boolean hasMuOrNon = words(primary).contains("mu") || words(primary).contains("non");
        if (!hasMuOrNon) return false;
        return haystack.contains("khoac")
                || haystack.contains("hoodie")
                || haystack.contains("jacket")
                || haystack.contains("co mu")
                || primary.startsWith("ao ")
                || primary.contains(" ao ");
    }

    private static boolean hitsTokens(Set<String> wordSet, Set<String> tokens) {
        for (String token : tokens) {
            if (wordSet.contains(normalize(token))) return true;
        }
        return false;
    }

    private static Set<String> words(String normalized) {
        if (normalized == null || normalized.isBlank()) return Set.of();
        return Arrays.stream(WORD_SPLIT.split(normalized))
                .filter(w -> !w.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String attributesText(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) return "";
        return attributes.values().stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(" "));
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
