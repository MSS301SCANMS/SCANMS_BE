package com.scanms.product.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Xoá cache catalog khi Store/Product thay đổi. Được gọi từ {@link CatalogEntityListener}
 * (@PostPersist/@PostUpdate/@PostRemove) nên bắt được mọi đường ghi — kể cả cập nhật bằng
 * dirty-checking không gọi save() (vd. rating gian hàng).
 * <p>
 * Việc xoá chạy SAU KHI transaction commit, gom một lần cho mỗi transaction, để request đọc
 * song song không nạp lại dữ liệu cũ vào cache trước khi DB kịp commit.
 */
@Component
public class CatalogCacheInvalidator {
    private static final Logger log = LoggerFactory.getLogger(CatalogCacheInvalidator.class);
    private static final Object TX_RESOURCE_KEY = CatalogCacheInvalidator.class.getName() + ".pending";

    static final List<String> STORE_CACHES = List.of(
            CacheNames.STORES_ALL, CacheNames.PUBLIC_STORES, CacheNames.PUBLIC_PRODUCTS, CacheNames.ADMIN_STATS_STORES);
    static final List<String> PRODUCT_CACHES = List.of(
            CacheNames.PRODUCTS_ALL, CacheNames.PUBLIC_PRODUCTS, CacheNames.PUBLIC_CATEGORIES,
            CacheNames.PUBLIC_STORES, CacheNames.ADMIN_STATS_PRODUCTS);

    /** Entity listener do Hibernate tạo nên truy cập bean qua tham chiếu tĩnh này. */
    private static volatile CatalogCacheInvalidator instance;

    private final CacheManager cacheManager;

    public CatalogCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        instance = this;
    }

    static void storeChanged() {
        CatalogCacheInvalidator current = instance;
        if (current != null) current.evictAfterCommit(STORE_CACHES);
    }

    static void productChanged() {
        CatalogCacheInvalidator current = instance;
        if (current != null) current.evictAfterCommit(PRODUCT_CACHES);
    }

    @SuppressWarnings("unchecked")
    void evictAfterCommit(List<String> cacheNames) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clear(cacheNames);
            return;
        }
        Set<String> pending = (Set<String>) TransactionSynchronizationManager.getResource(TX_RESOURCE_KEY);
        if (pending == null) {
            Set<String> created = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(TX_RESOURCE_KEY, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear(created);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(TX_RESOURCE_KEY);
                }
            });
            pending = created;
        }
        pending.addAll(cacheNames);
    }

    private void clear(Iterable<String> cacheNames) {
        for (String name : cacheNames) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) cache.clear();
            } catch (RuntimeException ex) {
                // Redis lỗi: cache sẽ tự hết hạn theo TTL, không làm hỏng thao tác ghi.
                log.warn("Không xoá được cache {}: {}", name, ex.getMessage());
            }
        }
    }
}
