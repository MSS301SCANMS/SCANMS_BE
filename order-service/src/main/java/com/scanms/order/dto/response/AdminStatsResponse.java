package com.scanms.order.dto.response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Thống kê tổng hợp cho dashboard admin. Toàn bộ được COUNT/GROUP BY trong DB,
 * các chuỗi thời gian đã được lấp đủ bucket (bucket rỗng = 0). Serializable để lưu cache Redis.
 */
public record AdminStatsResponse(
        long total,
        long newToday,
        long newLast7Days,
        long newLast30Days,
        Map<String, Long> byStatus,
        /** 24 bucket theo giờ, kết thúc ở giờ hiện tại. */
        List<Bucket> hourly,
        /** 30 bucket theo ngày, kết thúc ở hôm nay. */
        List<Bucket> daily,
        /** 6 bucket theo tháng, kết thúc ở tháng hiện tại. */
        List<Bucket> monthly,
        LocalDateTime generatedAt
) implements Serializable {
    public record Bucket(LocalDateTime start, long count) implements Serializable {}
}
