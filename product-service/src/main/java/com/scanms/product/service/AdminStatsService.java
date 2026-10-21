package com.scanms.product.service;

import com.scanms.product.config.CacheNames;
import com.scanms.product.dto.response.AdminStatsResponse;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thống kê gian hàng/sản phẩm — cache Redis 15 giây, và bị xoá ngay khi Store/Product thay đổi
 * (xem {@link com.scanms.product.config.CatalogCacheInvalidator}).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {
    private final StoreRepository stores;
    private final ProductRepository products;

    @Cacheable(CacheNames.ADMIN_STATS_STORES)
    @Transactional(readOnly = true)
    public AdminStatsResponse storeStats() {
        return AdminStatsBuilder.build(new AdminStatsBuilder.Source() {
            @Override public long total() { return stores.count(); }
            @Override public long countSince(LocalDateTime from) { return stores.countByCreatedAtGreaterThanEqual(from); }
            @Override public List<Object[]> countByStatus() { return stores.countGroupByStatus(); }
            @Override public List<Object[]> countPerHourSince(LocalDateTime from) { return stores.countPerHourSince(from); }
            @Override public List<Object[]> countPerDaySince(LocalDateTime from) { return stores.countPerDaySince(from); }
            @Override public List<Object[]> countPerMonthSince(LocalDateTime from) { return stores.countPerMonthSince(from); }
        });
    }

    @Cacheable(CacheNames.ADMIN_STATS_PRODUCTS)
    @Transactional(readOnly = true)
    public AdminStatsResponse productStats() {
        return AdminStatsBuilder.build(new AdminStatsBuilder.Source() {
            @Override public long total() { return products.count(); }
            @Override public long countSince(LocalDateTime from) { return products.countByCreatedAtGreaterThanEqual(from); }
            @Override public List<Object[]> countByStatus() { return products.countGroupByStatus(); }
            @Override public List<Object[]> countPerHourSince(LocalDateTime from) { return products.countPerHourSince(from); }
            @Override public List<Object[]> countPerDaySince(LocalDateTime from) { return products.countPerDaySince(from); }
            @Override public List<Object[]> countPerMonthSince(LocalDateTime from) { return products.countPerMonthSince(from); }
        });
    }
}
