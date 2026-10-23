package com.scanms.order.service.impl;

import com.scanms.order.service.ManagerStatisticsService;

import com.scanms.order.constant.*;
import com.scanms.order.dto.response.ManagerStatisticsResponse;
import com.scanms.order.dto.response.ManagerStatisticsResponse.*;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ManagerStatisticsServiceImpl implements ManagerStatisticsService {
    private final OrderRepository orders;
    private final SellerOrderRepository sellers;
    private final OrderItemRepository items;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<OrderStatus> PAID = Set.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED);

    @Transactional(readOnly = true)
    public ManagerStatisticsResponse summary(String range, LocalDate anchor) {
        if (anchor == null) anchor = LocalDate.now(VIETNAM);
        if (anchor.getYear() < 2000 || anchor.getYear() > 2100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Năm thống kê phải từ 2000 đến 2100.");
        LocalDate from = switch (range) {
            case "week" -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> anchor.withDayOfMonth(1);
            case "year" -> anchor.withDayOfYear(1);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kỳ thống kê không hợp lệ.");
        };
        LocalDate until = switch (range) { case "week" -> from.plusWeeks(1);
        case "month" -> from.plusMonths(1);
        default -> from.plusYears(1);
        };
        // Hibernate @CreationTimestamp uses the application's local JVM time for LocalDateTime columns.
        ZoneId storage = ZoneId.systemDefault();
        var records = orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                from.atStartOfDay(VIETNAM).withZoneSameInstant(storage).toLocalDateTime(),
                until.atStartOfDay(VIETNAM).withZoneSameInstant(storage).toLocalDateTime());
        Map<String, Order> byOrder = records.stream().collect(Collectors.toMap(Order::getOrderId, order -> order));
        Map<LocalDate, long[]> daily = new TreeMap<>();
        from.datesUntil(until).forEach(day -> daily.put(day, new long[3]));
        Map<String, LocalDate> orderDays = new HashMap<>();
        long paidCount = 0, cancelledCount = 0;
        for (Order order : records) {
            LocalDate day = order.getCreatedAt().atZone(storage).withZoneSameInstant(VIETNAM).toLocalDate();
            orderDays.put(order.getOrderId(), day);
            daily.get(day)[0]++;
            if (order.getStatus() != null && PAID.contains(order.getStatus())) { daily.get(day)[1]++; paidCount++; }
            if (order.getStatus() == OrderStatus.CANCELLED) cancelledCount++;
        }
        Map<String, SellerOrder> eligible = new HashMap<>();
        if (!byOrder.isEmpty()) for (SellerOrder seller : sellers.findByOrderIdIn(byOrder.keySet())) {
            Order order = byOrder.get(seller.getOrderId());
            if (seller.getStatus() != SellerOrderStatus.CANCELLED && order != null && order.getStatus() != null && PAID.contains(order.getStatus()))
                eligible.put(seller.getSellerOrderId(), seller);
        }
        Map<String, Bucket> storeBuckets = new HashMap<>(), productBuckets = new HashMap<>();
        long revenue = 0, quantity = 0;
        if (!eligible.isEmpty()) for (OrderItem item : items.findBySellerOrderIdIn(eligible.keySet())) {
            SellerOrder seller = eligible.get(item.getSellerOrderId());
            if (seller == null) continue;
            if (item.getNetPaidAmountVnd() == null || item.getQuantity() == null || item.getNetPaidAmountVnd() < 0 || item.getQuantity() < 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Có dòng hàng thiếu số tiền hoặc số lượng. Vui lòng kiểm tra dữ liệu đơn hàng.");
            long amount = item.getNetPaidAmountVnd();
            revenue = Math.addExact(revenue, amount); quantity = Math.addExact(quantity, item.getQuantity());
            daily.get(orderDays.get(seller.getOrderId()))[2] = Math.addExact(daily.get(orderDays.get(seller.getOrderId()))[2], amount);
            Object storeName = seller.getTotalsSnapshot() == null ? null : seller.getTotalsSnapshot().get("storeName");
            if (
                    seller.getStoreId() != null
                    ) storeBuckets.computeIfAbsent(
                    seller.getStoreId(),
                    id -> new Bucket(id, storeName == null ? null : storeName.toString())
                    ).add(
                    seller.getOrderId(),
                    item.getQuantity(),
                    amount
                    );
            if (item.getProductId() != null) {
                Object name = item.getProductSnapshot() == null ? null : item.getProductSnapshot().getOrDefault("productName", item.getProductSnapshot().get("name"));
                productBuckets.computeIfAbsent(
                        item.getProductId(),
                        id -> new Bucket(id, name == null ? null : name.toString())
                        ).add(
                        seller.getOrderId(),
                        item.getQuantity(),
                        amount
                        );
            }
        }
        return new ManagerStatisticsResponse(range, from, until.minusDays(1), Instant.now(), records.size(), paidCount, cancelledCount,
                revenue, quantity, storeBuckets.size(
                        
                        ), daily.entrySet(
                        
                        ).stream(
                        
                        ).map(
                        entry -> new Daily(entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2])
                        ).toList(
                        
                        ), top(
                        storeBuckets
                        ), top(
                        productBuckets
                        ));
    }
    private List<Ranking> top(Map<String, Bucket> buckets) {
        return buckets.values().stream().sorted(Comparator.comparingLong((Bucket bucket) -> bucket.revenue).reversed().thenComparing(bucket -> bucket.id))
                .limit(10).map(bucket -> new Ranking(bucket.id, bucket.name, bucket.orderIds.size(), bucket.quantity, bucket.revenue)).toList();
    }
    private static class Bucket {
        final String id, name; final Set<String> orderIds = new HashSet<>(); long quantity, revenue;
        Bucket(String id, String name) { this.id = id; this.name = name; }
        void add(
                String orderId,
                long count,
                long amount
                ) {
                    orderIds.add(orderId);
                    quantity = Math.addExact(quantity, count);
                    revenue = Math.addExact(revenue, amount);
                }
    }
}
