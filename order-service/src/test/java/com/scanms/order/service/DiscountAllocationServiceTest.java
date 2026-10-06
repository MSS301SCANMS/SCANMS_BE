package com.scanms.order.service;

import com.scanms.order.entity.OrderItem;
import com.scanms.order.mapper.DiscountAllocationMapper;
import com.scanms.order.repository.DiscountAllocationRepository;
import com.scanms.order.service.impl.DiscountAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DiscountAllocationServiceTest {

    @Mock
    private DiscountAllocationRepository repository;

    @Mock
    private DiscountAllocationMapper mapper;

    private DiscountAllocationServiceImpl discountAllocationService;

    @BeforeEach
    void setUp() {
        discountAllocationService = new DiscountAllocationServiceImpl(repository, mapper);
    }

    @Test
    @DisplayName("Should return 0 allocation when discount is 0 or null")
    void testZeroOrNullDiscount() {
        OrderItem item1 = OrderItem.builder().orderItemId("item-1").grossAmountVnd(200000L).build();
        OrderItem item2 = OrderItem.builder().orderItemId("item-2").grossAmountVnd(300000L).build();

        Map<String, Long> resultZero = discountAllocationService.allocateOrderDiscount(List.of(item1, item2), 0L);
        assertEquals(0L, resultZero.get("item-1"));
        assertEquals(0L, resultZero.get("item-2"));

        Map<String, Long> resultNull = discountAllocationService.allocateOrderDiscount(List.of(item1, item2), null);
        assertEquals(0L, resultNull.get("item-1"));
        assertEquals(0L, resultNull.get("item-2"));
    }

    @Test
    @DisplayName("Should allocate proportionally between two items with exact division")
    void testEqualProportionalAllocation() {
        OrderItem item1 = OrderItem.builder().orderItemId("item-1").grossAmountVnd(500000L).build();
        OrderItem item2 = OrderItem.builder().orderItemId("item-2").grossAmountVnd(500000L).build();

        // 100,000 VND discount on 1,000,000 VND total gross -> each gets 50,000 VND
        Map<String, Long> result = discountAllocationService.allocateOrderDiscount(List.of(item1, item2), 100000L);

        assertEquals(50000L, result.get("item-1"));
        assertEquals(50000L, result.get("item-2"));
        assertEquals(100000L, result.get("item-1") + result.get("item-2"));
    }

    @Test
    @DisplayName("Should allocate proportionally and resolve penny rounding drift to exact sum")
    void testProportionalAllocationWithPennyRounding() {
        // Store A: 400.000đ, Store A item 2: 300.000đ, Store B: 500.000đ -> total 1.200.000đ
        // Discount: 100.000đ (approx 8.33333%)
        OrderItem item1 = OrderItem.builder().orderItemId("item-1").grossAmountVnd(400000L).build();
        OrderItem item2 = OrderItem.builder().orderItemId("item-2").grossAmountVnd(300000L).build();
        OrderItem item3 = OrderItem.builder().orderItemId("item-3").grossAmountVnd(500000L).build();

        Map<String, Long> result = discountAllocationService.allocateOrderDiscount(List.of(item1, item2, item3), 100000L);

        long totalAllocated = result.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(100000L, totalAllocated, "Total allocated discount MUST exactly equal the discount amount without losing a single VND");

        // Verify each item gets a non-negative discount not exceeding gross
        assertTrue(result.get("item-1") <= 400000L && result.get("item-1") > 0);
        assertTrue(result.get("item-2") <= 300000L && result.get("item-2") > 0);
        assertTrue(result.get("item-3") <= 500000L && result.get("item-3") > 0);
    }

    @Test
    @DisplayName("Should cap allocation when discount exceeds total gross")
    void testDiscountExceedingTotalGross() {
        OrderItem item1 = OrderItem.builder().orderItemId("item-1").grossAmountVnd(50000L).build();
        OrderItem item2 = OrderItem.builder().orderItemId("item-2").grossAmountVnd(50000L).build();

        // 200,000 VND discount on 100,000 VND gross -> capped at 100,000 VND total
        Map<String, Long> result = discountAllocationService.allocateOrderDiscount(List.of(item1, item2), 200000L);

        long totalAllocated = result.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(100000L, totalAllocated);
        assertEquals(50000L, result.get("item-1"));
        assertEquals(50000L, result.get("item-2"));
    }
}
