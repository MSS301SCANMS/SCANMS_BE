package com.scanms.order.service.impl;

import com.scanms.order.config.CacheNames;
import com.scanms.order.dto.response.AdminStatsResponse;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.service.AdminStatsBuilder;
import com.scanms.order.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thống kê đơn hàng — cache Redis 15 giây ({@link CacheNames#ADMIN_STATS_ORDERS}).
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {
    private final OrderRepository orders;

    @Override
    @Cacheable(CacheNames.ADMIN_STATS_ORDERS)
    @Transactional(readOnly = true)
    public AdminStatsResponse orderStats() {
        return AdminStatsBuilder.build(new AdminStatsBuilder.Source() {
            @Override
            public long total() {
                return orders.count();
            }

            @Override
            public long countSince(LocalDateTime from) {
                return orders.countByCreatedAtGreaterThanEqual(from);
            }

            @Override
            public List<Object[]> countByStatus() {
                return orders.countGroupByStatus();
            }

            @Override
            public List<Object[]> countPerHourSince(LocalDateTime from) {
                return orders.countPerHourSince(from);
            }

            @Override
            public List<Object[]> countPerDaySince(LocalDateTime from) {
                return orders.countPerDaySince(from);
            }

            @Override
            public List<Object[]> countPerMonthSince(LocalDateTime from) {
                return orders.countPerMonthSince(from);
            }
        });
    }
}
