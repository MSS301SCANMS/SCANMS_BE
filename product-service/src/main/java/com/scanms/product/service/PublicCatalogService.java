package com.scanms.product.service;

import com.scanms.product.config.CacheNames;
import com.scanms.product.constant.ProductStatus;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dữ liệu catalog công khai (marketplace / storefront), cache trong Redis.
 * Trước đây mỗi request /public/products đều findAll() toàn bộ sản phẩm + gian hàng; nay chỉ
 * truy vấn DB khi cache trống/hết hạn, còn lọc/sắp xếp/phân trang làm trên bản cache.
 * Cache bị xoá ngay khi Store/Product thay đổi (xem CatalogCacheInvalidator).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCatalogService {
    private final ProductRepository products;
    private final StoreRepository stores;

    /** Sản phẩm được hiển thị công khai (ACTIVE / OUT_OF_STOCK / chưa đặt trạng thái), mới nhất trước. */
    @Cacheable(CacheNames.PUBLIC_PRODUCTS)
    public List<Map<String, Object>> visibleProducts() {
        Map<String, Store> storeById = stores.findAll().stream()
                .collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));
        return products.findAll().stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == ProductStatus.ACTIVE
                        || p.getStatus() == ProductStatus.OUT_OF_STOCK)
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> toPublicProduct(p, storeById.get(p.getStoreId())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Gian hàng đã duyệt (marketplace), kèm rating. */
    @Cacheable(CacheNames.PUBLIC_STORES)
    public List<Map<String, Object>> approvedStores() {
        return stores.findAll().stream()
                .filter(s -> s.getApprovalStatus() == StoreStatus.APPROVED)
                .map(PublicCatalogService::toPublicStore)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Danh mục (tên category) suy ra từ toàn bộ sản phẩm. */
    @Cacheable(CacheNames.PUBLIC_CATEGORIES)
    public List<Map<String, Object>> categories() {
        return products.findAll().stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", name);
                    row.put("name", name);
                    return row;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static Map<String, Object> toPublicProduct(Product p, Store store) {
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
            Map<String, Object> storeInfo = new LinkedHashMap<>();
            storeInfo.put("id", store.getStoreId());
            storeInfo.put("name", store.getName());
            storeInfo.put("logoUrl", store.getLogoRef());
            row.put("store", storeInfo);
        }
        return row;
    }

    public static Map<String, Object> toPublicStore(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getStoreId());
        row.put("storeId", store.getStoreId());
        row.put("name", store.getName());
        row.put("description", store.getDescription());
        row.put("logoUrl", store.getLogoRef());
        row.put("approvalStatus", store.getApprovalStatus());
        row.put("ratingAverage", store.getRatingAverage());
        row.put("ratingCount", store.getRatingCount());
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
