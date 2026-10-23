package com.scanms.product.service.impl;

import com.scanms.product.config.CacheNames;
import com.scanms.product.dto.response.AdminStatsResponse;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.AdminStatsBuilder;
import com.scanms.product.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thống kê gian hàng/sản phẩm — cache Redis 15 giây, và bị xoá ngay khi Store/Product thay đổi
 * (xem {@link com.scanms.product.config.CatalogCacheInvalidator}).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {
    private final StoreRepository stores;
    private final ProductRepository products;

    @Override
    @Cacheable(CacheNames.ADMIN_STATS_STORES)
    @Transactional(readOnly = true)
    public AdminStatsResponse storeStats() {
        return AdminStatsBuilder.build(new AdminStatsBuilder.Source() {
            @Override
            public long total() {
                return stores.count();
            }

            @Override
            public long countSince(LocalDateTime from) {
                return stores.countByCreatedAtGreaterThanEqual(from);
            }

            @Override
            public List<Object[]> countByStatus() {
                return stores.countGroupByStatus();
            }

            @Override
            public List<Object[]> countPerHourSince(LocalDateTime from) {
                return stores.countPerHourSince(from);
            }

            @Override
            public List<Object[]> countPerDaySince(LocalDateTime from) {
                return stores.countPerDaySince(from);
            }

            @Override
            public List<Object[]> countPerMonthSince(LocalDateTime from) {
                return stores.countPerMonthSince(from);
            }
        });
    }

    @Override
    @Cacheable(CacheNames.ADMIN_STATS_PRODUCTS)
    @Transactional(readOnly = true)
    public AdminStatsResponse productStats() {
        return AdminStatsBuilder.build(new AdminStatsBuilder.Source() {
            @Override
            public long total() {
                return products.count();
            }

            @Override
            public long countSince(LocalDateTime from) {
                return products.countByCreatedAtGreaterThanEqual(from);
            }

            @Override
            public List<Object[]> countByStatus() {
                return products.countGroupByStatus();
            }

            @Override
            public List<Object[]> countPerHourSince(LocalDateTime from) {
                return products.countPerHourSince(from);
            }

            @Override
            public List<Object[]> countPerDaySince(LocalDateTime from) {
                return products.countPerDaySince(from);
            }

            @Override
            public List<Object[]> countPerMonthSince(LocalDateTime from) {
                return products.countPerMonthSince(from);
            }
        });
    }
}
