package com.scanms.product.constant;

import java.util.*;

/**
 * Standardized fashion tags stored in {@code Product.attributes} for outfit AI matching.
 */
public final class FashionTags {
    private FashionTags() {}

    public static final String ATTR_APPAREL_TYPE = "apparelType";
    public static final String ATTR_FASHION_STYLE = "fashionStyle";
    public static final String ATTR_GENDER_TARGET = "genderTarget";
    public static final String ATTR_COLOR_TAGS = "colorTags";

    public static final Set<String> APPAREL_TYPES = Set.of(
            "SHOE", "HAT", "TOP", "BOTTOM", "OUTERWEAR", "DRESS", "SET", "OTHER");
    public static final Set<String> FASHION_STYLES = Set.of(
            "STREETWEAR", "CASUAL", "FORMAL", "SPORT", "CHIC", "MINIMAL", "OTHER");
    public static final Set<String> GENDER_TARGETS = Set.of("MALE", "FEMALE", "UNISEX");

    public static String normalizeApparelType(Object raw) {
        return normalizeEnum(raw, APPAREL_TYPES);
    }

    public static String normalizeFashionStyle(Object raw) {
        return normalizeEnum(raw, FASHION_STYLES);
    }

    public static String normalizeGenderTarget(Object raw) {
        return normalizeEnum(raw, GENDER_TARGETS);
    }

    @SuppressWarnings("unchecked")
    public static List<String> normalizeColorTags(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(v -> String.valueOf(v).trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) return List.of();
        return Arrays.stream(text.split("[,;/|]"))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    public static String inferApparelType(String title, String category) {
        String hay = normalizeText(title) + " " + normalizeText(category);
        if (containsAny(hay, "giay", "sneaker", "sandal", "boot", "dep", "loafer", "footwear", "cleat")) return "SHOE";
        if (containsAny(hay, "non ", " non", "mu ", " mu", "bucket", "beanie", "snapback", "cap ", " hat")) {
            // Avoid hooded jackets: "ao khoac ... mu"
            if (containsAny(hay, "ao khoac", "hoodie", "jacket", "cardigan")) return "OUTERWEAR";
            if (containsAny(hay, "non", "mu luoi", "mu bucket", "hat", "cap", "beanie", "beret", "fedora")) return "HAT";
        }
        if (containsAny(hay, "vay", "dam")) return "DRESS";
        if (containsAny(hay, "quan", "short", "jean", "jogger", "legging")) return "BOTTOM";
        if (containsAny(hay, "ao khoac", "hoodie", "blazer", "coat", "jacket", "cardigan")) return "OUTERWEAR";
        if (containsAny(hay, "ao ", "ao thun", "so mi", "tank", "crop")) return "TOP";
        if (containsAny(hay, "bo ", "set ", "outfit", "corset")) return "SET";
        return "OTHER";
    }

    public static String inferGenderTarget(String title, String category) {
        String hay = normalizeText(title) + " " + normalizeText(category);
        boolean female = containsAny(hay, "nu", "women", "woman", "ladies", "girl");
        boolean male = containsAny(hay, "nam", "men", "man", "male", "boy");
        if (female && !male) return "FEMALE";
        if (male && !female) return "MALE";
        if (containsAny(hay, "unisex")) return "UNISEX";
        return "UNISEX";
    }

    public static String inferFashionStyle(String title, String category, String description) {
        String hay = normalizeText(title) + " " + normalizeText(category) + " " + normalizeText(description);
        if (containsAny(hay, "streetwear", "street")) return "STREETWEAR";
        if (containsAny(hay, "sport", "athletic", "running", "baseball", "tennis")) return "SPORT";
        if (containsAny(hay, "formal", "cong so", "vest", "blazer")) return "FORMAL";
        if (containsAny(hay, "chic", "elegant")) return "CHIC";
        if (containsAny(hay, "minimal")) return "MINIMAL";
        if (containsAny(hay, "casual")) return "CASUAL";
        return "CASUAL";
    }

    private static String normalizeEnum(Object raw, Set<String> allowed) {
        if (raw == null) return null;
        String value = String.valueOf(raw).trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return allowed.contains(value) ? value : null;
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        String nfd = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return nfd.toLowerCase(Locale.ROOT).replace('đ', 'd');
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
