package com.scanms.product.service;

import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.scanms.product.constant.StoreStatus;

public interface PublicCatalogService {
    /** Sản phẩm được hiển thị công khai (ACTIVE / OUT_OF_STOCK / chưa đặt trạng thái), mới nhất trước. */
    List<Map<String, Object>> visibleProducts();

    /** Read current variant stock directly; detail/stock endpoints must not serve cached inventory. */
    Map<String, Object> productDetails(Product product, Store store);

    /** Gian hàng đã duyệt (marketplace), kèm rating. */
    List<Map<String, Object>> approvedStores();

    /** Danh mục (tên category) suy ra từ toàn bộ sản phẩm. */
    List<Map<String, Object>> categories();

    static Map<String, Object> toPublicProduct(Product p, Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getProductId());
        row.put("productId", p.getProductId());
        row.put("storeId", p.getStoreId());
        row.put("title", p.getName());
        row.put("name", p.getName());
        row.put("description", p.getDescription());
        row.put("categoryId", p.getCategoryId());
        row.put("categoryName", p.getCategory());
        row.put("category", p.getCategory());
        row.put("price", p.getPriceVnd() != null ? p.getPriceVnd() : 0);
        row.put("priceVnd", p.getPriceVnd() != null ? p.getPriceVnd() : 0);
        row.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        row.put("imageUrl", firstImage(p.getImageRefs()));
        row.put("imageRefs", p.getImageRefs());
        row.put("sizesStock", p.getSizesStock());
        row.put("attributes", p.getAttributes());
        row.put("createdAt", p.getCreatedAt());
        if (store != null) {
            Map<String, Object> storeInfo = toPublicStore(store);
            row.put("store", storeInfo);
        }
        return row;
    }

    static Map<String, Object> toPublicStore(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getStoreId());
        row.put("storeId", store.getStoreId());
        row.put("slug", store.getStoreId());
        row.put("name", store.getName());
        row.put("description", store.getDescription());
        row.put("logoUrl", store.getLogoRef());
        row.put("approvalStatus", store.getApprovalStatus());
        row.put("ratingAverage", store.getRatingAverage());
        row.put("ratingCount", store.getRatingCount());
        row.put("rating", store.getRatingAverage());
        row.put("reviewCount", store.getRatingCount() == null ? 0L : store.getRatingCount());
        row.put("isVerified", store.getApprovalStatus() == StoreStatus.APPROVED);
        row.put("isActive", store.getApprovalStatus() == StoreStatus.APPROVED);
        row.put("followerCount", 0);
        return row;
    }

    private static String firstImage(Map<String, Object> imageRefs) {
        if (imageRefs == null || imageRefs.isEmpty()) return null;
        Object main = imageRefs.get("main");
        if (main != null && !String.valueOf(main).isBlank()) return String.valueOf(main);
        Object first = imageRefs.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
        return first != null ? String.valueOf(first) : null;
    }
}
