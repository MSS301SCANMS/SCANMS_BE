package com.scanms.user.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cache dùng chung qua Redis (nhiều instance cùng thấy một bản cache).
 * <ul>
 *   <li>Key: {@code scanms:user:cache:v1:<cache>::<key>} — đổi "v1" khi thay cấu trúc DTO để bỏ cache cũ.</li>
 *   <li>Lỗi Redis (mất kết nối, giải mã thất bại…) chỉ được log, request vẫn đọc DB bình thường.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {
    private static final String KEY_PREFIX = "scanms:user:cache:v1:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig(getClass().getClassLoader())
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + "::");
        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        CacheNames.ttls().forEach((name, ttl) -> perCache.put(name, defaults.entryTtl(ttl)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
