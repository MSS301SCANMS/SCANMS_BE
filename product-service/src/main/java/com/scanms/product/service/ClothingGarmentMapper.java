package com.scanms.product.service;

import java.util.Locale;
import java.util.Map;

/**
 * Maps YOLO fashion class names to shop/customer-facing garment hints.
 * Detection remains advisory; sellers confirm before persistence.
 */
public final class ClothingGarmentMapper {
    public static final String SUGGESTED_CATEGORY = "Thời trang & Phụ kiện";

    public enum GarmentType {
        TOP,
        BOTTOM,
        OUTERWEAR,
        OTHER_CLOTHING,
        UNKNOWN
    }

    private static final Map<String, String> DISPLAY_LABELS = Map.ofEntries(
            Map.entry("short_sleeve_top", "Áo tay ngắn"),
            Map.entry("long_sleeve_top", "Áo tay dài"),
            Map.entry("short_sleeve_outwear", "Áo khoác tay ngắn"),
            Map.entry("long_sleeve_outwear", "Áo khoác tay dài"),
            Map.entry("vest", "Áo vest"),
            Map.entry("sling", "Áo hai dây"),
            Map.entry("shorts", "Quần short"),
            Map.entry("trousers", "Quần dài"),
            Map.entry("skirt", "Chân váy"),
            Map.entry("short_sleeve_dress", "Váy tay ngắn"),
            Map.entry("long_sleeve_dress", "Váy tay dài"),
            Map.entry("vest_dress", "Váy vest"),
            Map.entry("sling_dress", "Váy hai dây"));

    private ClothingGarmentMapper() {}

    public static String normalize(String className) {
        if (className == null) return "";
        return className.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public static GarmentType garmentType(String className) {
        String key = normalize(className);
        if (key.isBlank()) return GarmentType.UNKNOWN;
        if (key.contains("trouser") || key.contains("pants") || key.contains("jean") || key.contains("skirt")
                || key.equals("shorts")
                || (key.contains("short") && !key.contains("sleeve") && !key.contains("dress"))) {
            return GarmentType.BOTTOM;
        }
        if (key.contains("outwear") || key.contains("outerwear") || key.contains("jacket") || key.contains("coat")
                || key.equals("vest")) {
            return GarmentType.OUTERWEAR;
        }
        if (key.endsWith("_top") || key.equals("top") || key.contains("shirt") || key.contains("blouse")
                || key.contains("hoodie") || key.contains("polo") || key.contains("_tee") || key.equals("tee")
                || key.contains("sling") || key.contains("dress")) {
            return GarmentType.TOP;
        }
        if (DISPLAY_LABELS.containsKey(key) || key.contains("cloth") || key.contains("apparel") || key.contains("fashion")) {
            return GarmentType.OTHER_CLOTHING;
        }
        return GarmentType.UNKNOWN;
    }

    public static boolean isClothing(String className) {
        return garmentType(className) != GarmentType.UNKNOWN;
    }

    public static String displayLabel(String className) {
        String key = normalize(className);
        if (DISPLAY_LABELS.containsKey(key)) return DISPLAY_LABELS.get(key);
        if (key.isBlank()) return "Không nhận diện";
        return switch (garmentType(key)) {
            case TOP -> "Áo / trang phục trên";
            case BOTTOM -> "Quần / trang phục dưới";
            case OUTERWEAR -> "Áo khoác";
            case OTHER_CLOTHING -> "Trang phục";
            case UNKNOWN -> className.replace('_', ' ');
        };
    }

    public static String suggestedCategory(String className) {
        return isClothing(className) ? SUGGESTED_CATEGORY : null;
    }
}
