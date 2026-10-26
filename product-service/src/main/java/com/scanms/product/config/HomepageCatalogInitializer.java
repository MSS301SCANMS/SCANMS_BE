package com.scanms.product.config;

import com.scanms.product.service.HomepageCatalogSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scanms.catalog.seed-enabled", havingValue = "true", matchIfMissing = true)
public class HomepageCatalogInitializer implements ApplicationRunner {
    private final HomepageCatalogSeedService seed;
    private final CatalogCacheInvalidator cache;

    @Override
    public void run(ApplicationArguments args) {
        var result = seed.seedIfEmpty();
        if (result.seeded()) cache.evictAfterCommit(java.util.List.of(
                CacheNames.STORES_ALL, CacheNames.PRODUCTS_ALL, CacheNames.PUBLIC_PRODUCTS,
                CacheNames.PUBLIC_STORES, CacheNames.PUBLIC_CATEGORIES,
                CacheNames.ADMIN_STATS_STORES, CacheNames.ADMIN_STATS_PRODUCTS));
        log.info("Homepage catalog: {} ({} products, {} variants, {} images)",
                result.seeded() ? "seeded empty database" : "existing data preserved",
                result.products(), result.variants(), result.images());
    }
}
