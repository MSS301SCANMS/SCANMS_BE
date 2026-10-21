package com.scanms.product.config;

import java.time.Duration;
import java.util.Map;

/** Tên cache Redis và thời gian sống (TTL) tương ứng. */
public final class CacheNames {
    private CacheNames() {}

    /** GET /api/v1/stores — danh sách gian hàng (admin). */
    public static final String STORES_ALL = "stores:all";

    /** GET /api/v1/products — danh sách sản phẩm. */
    public static final String PRODUCTS_ALL = "products:all";

    /** Catalog public: sản phẩm đang hiển thị (đã gắn thông tin gian hàng). */
    public static final String PUBLIC_PRODUCTS = "public:products";

    /** Gian hàng đã duyệt (marketplace). */
    public static final String PUBLIC_STORES = "public:stores";

    /** Danh mục suy ra từ sản phẩm. */
    public static final String PUBLIC_CATEGORIES = "public:categories";

    /** Thống kê gian hàng cho dashboard admin. */
    public static final String ADMIN_STATS_STORES = "admin-stats:stores";

    /** Thống kê sản phẩm cho dashboard admin. */
    public static final String ADMIN_STATS_PRODUCTS = "admin-stats:products";

    /** TTL theo từng cache; cache không khai báo dùng TTL mặc định trong {@link RedisCacheConfig}. */
    public static Map<String, Duration> ttls() {
        return Map.ofEntries(
                Map.entry(STORES_ALL, Duration.ofMinutes(10)),
                Map.entry(PRODUCTS_ALL, Duration.ofMinutes(10)),
                Map.entry(PUBLIC_PRODUCTS, Duration.ofMinutes(10)),
                Map.entry(PUBLIC_STORES, Duration.ofMinutes(10)),
                Map.entry(PUBLIC_CATEGORIES, Duration.ofMinutes(10)),
                Map.entry(ADMIN_STATS_STORES, Duration.ofSeconds(15)),
                Map.entry(ADMIN_STATS_PRODUCTS, Duration.ofSeconds(15))
        );
    }
}
