package com.scanms.product.config;

import com.scanms.product.service.SoraCatalogSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = {"scanms.catalog.seed-enabled", "scanms.catalog.sora-seed-enabled"}, havingValue = "true", matchIfMissing = true)
public class SoraCatalogInitializer implements ApplicationRunner {
    private final SoraCatalogSeedService seed;
    private final CatalogCacheInvalidator cache;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seed.seed();
        cache.evictAfterCommit(java.util.List.of(CacheNames.STORES_ALL, CacheNames.PRODUCTS_ALL,
                CacheNames.PUBLIC_PRODUCTS, CacheNames.PUBLIC_STORES, CacheNames.PUBLIC_CATEGORIES,
                CacheNames.ADMIN_STATS_STORES, CacheNames.ADMIN_STATS_PRODUCTS));
        log.info("Sora Skin clothing seed checked; existing inventory and shop ownership preserved");
    }
}
