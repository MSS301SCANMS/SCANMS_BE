package com.scanms.order.service;

import com.scanms.order.constant.*;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import com.scanms.order.service.impl.ManagerStatisticsServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagerStatisticsServiceTest {
    OrderRepository orders = mock(OrderRepository.class);
    SellerOrderRepository sellers = mock(SellerOrderRepository.class);
    OrderItemRepository items = mock(OrderItemRepository.class);
    ManagerStatisticsService service = new ManagerStatisticsServiceImpl(orders, sellers, items);
    LocalDateTime time(String day) { return LocalDate.parse(day).atTime(12, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); }
    Order order(String id, OrderStatus status) { return Order.builder().orderId(id).status(status).createdAt(time("2026-10-21")).build(); }
    SellerOrder seller(String id, String order, String store, SellerOrderStatus status) { return SellerOrder.builder().sellerOrderId(id).orderId(order).storeId(store).status(status).totalsSnapshot(Map.of("storeName", store)).build(); }
    OrderItem line(String seller, String product, int count, long paid) { return OrderItem.builder().sellerOrderId(seller).productId(product).productSnapshot(Map.of("name", product)).quantity(count).netPaidAmountVnd(paid).build(); }
    @Test void aggregatesPaidLinesWithoutDoubleCountingMultiStoreOrdersOrCancelledSellers() {
        when(orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(order("paid", OrderStatus.PAID), order("pending", OrderStatus.AWAITING_PAYMENT), order("cancelled", OrderStatus.CANCELLED), order("complete", OrderStatus.COMPLETED)));
        when(sellers.findByOrderIdIn(any())).thenReturn(List.of(seller("a", "paid", "store-1", SellerOrderStatus.CONFIRMED), seller("b", "paid", "store-2", SellerOrderStatus.CONFIRMED), seller("c", "paid", "store-3", SellerOrderStatus.CANCELLED), seller("d", "pending", "store-1", SellerOrderStatus.PENDING), seller("e", "complete", "store-1", SellerOrderStatus.COMPLETED)));
        when(items.findBySellerOrderIdIn(any())).thenAnswer(invocation -> {
            Set<?> ids = invocation.getArgument(0); assertEquals(Set.of("a", "b", "e"), ids);
            return List.of(line("a", "product-1", 2, 180_000), line("a", "product-1", 1, 90_000), line("b", "product-2", 1, 50_000), line("e", "product-1", 1, 100_000));
        });
        var result = service.summary("month", LocalDate.of(2026, 10, 21));
        assertEquals(4, result.orderCount()); assertEquals(2, result.paidOrderCount()); assertEquals(1, result.cancelledOrderCount());
        assertEquals(420_000, result.revenueVnd()); assertEquals(5, result.soldQuantity()); assertEquals(2, result.sellingStoreCount());
        assertEquals("store-1", result.topStores().getFirst().id()); assertEquals(370_000, result.topStores().getFirst().revenueVnd());
        assertEquals(2, result.topStores().getFirst().orderCount()); assertEquals(2, result.topProducts().getFirst().orderCount());
        assertEquals(31, result.daily().size()); assertEquals(420_000, result.daily().get(20).revenueVnd());
        assertEquals(0, result.daily().getFirst().revenueVnd());
    }
    @Test void handlesVietnamWeekYearBoundaryAndLeapYearWithExclusiveEnd() {
        when(orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of());
        var week = service.summary("week", LocalDate.of(2026, 1, 1));
        assertEquals(LocalDate.of(2025,12,29), week.fromDate()); assertEquals(LocalDate.of(2026,1,4), week.toDate()); assertEquals(7, week.daily().size());
        var vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
        verify(orders).findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDate.of(2025,12,29).atStartOfDay(vietnam).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(), LocalDate.of(2026,1,5).atStartOfDay(vietnam).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
        var leap = service.summary("year", LocalDate.of(2024, 2, 29));
        assertEquals(366, leap.daily().size()); assertEquals(0, leap.revenueVnd()); assertTrue(leap.topStores().isEmpty());
        verifyNoInteractions(sellers, items);
    }
    @Test void limitsRankingToTenAndUsesStableTieBreak() {
        when(orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(order("paid", OrderStatus.PAID)));
        var sellerRows = new ArrayList<SellerOrder>(); var itemRows = new ArrayList<OrderItem>();
        for (int i=0;i<12;i++) { String id=String.format("%02d",i); sellerRows.add(seller(id,"paid",id,SellerOrderStatus.CONFIRMED)); itemRows.add(line(id,id,1,100)); }
        when(sellers.findByOrderIdIn(any())).thenReturn(sellerRows); when(items.findBySellerOrderIdIn(any())).thenReturn(itemRows);
        var result=service.summary("month",LocalDate.of(2026,10,21));
        assertEquals(10,result.topStores().size()); assertEquals(12,result.sellingStoreCount()); assertEquals("00",result.topStores().getFirst().id()); assertEquals("09",result.topStores().getLast().id());
    }
    @Test void rejectsInvalidPeriodsAndIncompleteFinancialLines() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.summary("day",LocalDate.of(2026,10,21)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.summary("year",LocalDate.of(2200,1,1)));
        when(orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(List.of(order("paid",OrderStatus.PAID)));
        when(sellers.findByOrderIdIn(any())).thenReturn(List.of(seller("a","paid","store",SellerOrderStatus.COMPLETED)));
        var incomplete=line("a","product",1,100); incomplete.setNetPaidAmountVnd(null);
        when(items.findBySellerOrderIdIn(any())).thenReturn(List.of(incomplete));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.summary("month",LocalDate.of(2026,10,21)));
    }
}
