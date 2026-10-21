package com.scanms.order.service;

import com.scanms.order.dto.response.AdminStatsResponse;
import com.scanms.order.dto.response.AdminStatsResponse.Bucket;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;

/** Dựng {@link AdminStatsResponse} từ các truy vấn đếm/gom nhóm của một bảng. */
public final class AdminStatsBuilder {

    /** Các truy vấn cần cho một bảng (mỗi hàng trả về [bucket/status, count]). */
    public interface Source {
        long total();
        long countSince(LocalDateTime from);
        List<Object[]> countByStatus();
        List<Object[]> countPerHourSince(LocalDateTime from);
        List<Object[]> countPerDaySince(LocalDateTime from);
        List<Object[]> countPerMonthSince(LocalDateTime from);
    }

    private AdminStatsBuilder() {}

    public static AdminStatsResponse build(Source source) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS).minusHours(23);
        LocalDateTime dayStart = today.minusDays(29);
        LocalDateTime monthStart = today.withDayOfMonth(1).minusMonths(5);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : source.countByStatus()) {
            byStatus.put(row[0] == null ? "UNKNOWN" : row[0].toString(), toLong(row[1]));
        }

        return new AdminStatsResponse(
                source.total(),
                source.countSince(today),
                source.countSince(today.minusDays(6)),
                source.countSince(dayStart),
                byStatus,
                fill(source.countPerHourSince(hourStart), hourStart, 24, ChronoUnit.HOURS),
                fill(source.countPerDaySince(dayStart), dayStart, 30, ChronoUnit.DAYS),
                fill(source.countPerMonthSince(monthStart), monthStart, 6, ChronoUnit.MONTHS),
                now);
    }

    private static List<Bucket> fill(List<Object[]> rows, LocalDateTime start, int size, ChronoUnit unit) {
        Map<LocalDateTime, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            LocalDateTime key = toLocalDateTime(row[0]);
            if (key != null) counts.merge(key, toLong(row[1]), Long::sum);
        }
        List<Bucket> buckets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            LocalDateTime bucket = start.plus(i, unit);
            buckets.add(new Bucket(bucket, counts.getOrDefault(bucket, 0L)));
        }
        return buckets;
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof OffsetDateTime odt) return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        if (value instanceof Instant instant) return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        if (value instanceof Date date) return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return null;
    }
}
