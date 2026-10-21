package com.scanms.user.config;

import java.time.Duration;
import java.util.Map;

/** Tên cache Redis và thời gian sống (TTL) tương ứng. */
public final class CacheNames {
    private CacheNames() {}

    /** Thống kê người dùng cho dashboard admin. */
    public static final String ADMIN_STATS_USERS = "admin-stats:users";

    /** TTL theo từng cache; cache không khai báo dùng TTL mặc định trong {@link RedisCacheConfig}. */
    public static Map<String, Duration> ttls() {
        return Map.ofEntries(
                Map.entry(ADMIN_STATS_USERS, Duration.ofSeconds(15))
        );
    }
}
