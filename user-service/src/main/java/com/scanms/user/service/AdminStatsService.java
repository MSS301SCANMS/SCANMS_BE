package com.scanms.user.service;

import com.scanms.user.config.CacheNames;
import com.scanms.user.dto.response.AdminStatsResponse;
import com.scanms.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thống kê người dùng — cache Redis 15 giây ({@link CacheNames#ADMIN_STATS_USERS}). */
@Service
@RequiredArgsConstructor
public class AdminStatsService {
    private final UserRepository users;

    @Cacheable(CacheNames.ADMIN_STATS_USERS)
    @Transactional(readOnly = true)
    public AdminStatsResponse userStats() {
        return AdminStatsBuilder.build(new AdminStatsBuilder.Source() {
            @Override
            public long total() {
                return users.count();
            }

            @Override
            public long countSince(LocalDateTime from) {
                return users.countByCreatedAtGreaterThanEqual(from);
            }

            @Override
            public List<Object[]> countByStatus() {
                return users.countGroupByStatus();
            }

            @Override
            public List<Object[]> countPerHourSince(LocalDateTime from) {
                return users.countPerHourSince(from);
            }

            @Override
            public List<Object[]> countPerDaySince(LocalDateTime from) {
                return users.countPerDaySince(from);
            }

            @Override
            public List<Object[]> countPerMonthSince(LocalDateTime from) {
                return users.countPerMonthSince(from);
            }

        });
    }
}
