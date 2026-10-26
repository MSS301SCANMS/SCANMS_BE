package com.scanms.product.dto.response;

public record OutfitProductCard(
        String productId,
        String title,
        String imageUrl,
        Long priceVnd,
        String storeId,
        String storeName,
        String categoryName,
        String matchReason,
        double matchScore) {
}
