package com.scanms.order.dto.response;

import java.time.*;
import java.util.List;

public record ManagerStatisticsResponse(String range, LocalDate fromDate, LocalDate toDate,
        Instant generatedAt, long orderCount, long paidOrderCount, long cancelledOrderCount,
        long revenueVnd, long soldQuantity, long sellingStoreCount,
        List<Daily> daily, List<Ranking> topStores, List<Ranking> topProducts) {
    public record Daily(LocalDate date, long orderCount, long paidOrderCount, long revenueVnd) {}
    public record Ranking(String id, String name, long orderCount, long quantity, long revenueVnd) {}
}
