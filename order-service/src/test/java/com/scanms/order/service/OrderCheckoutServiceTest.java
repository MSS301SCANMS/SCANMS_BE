package com.scanms.order.service;

import com.scanms.order.client.ProductClient;
import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.request.CheckoutItemRequest;
import com.scanms.order.dto.request.CheckoutRequest;
import com.scanms.order.dto.response.CheckoutResponse;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.entity.Shipment;
import com.scanms.order.mapper.OrderItemMapper;
import com.scanms.order.mapper.OrderMapper;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private SellerOrderRepository sellerOrderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private DiscountAllocationService discountAllocationService;
    @Mock
    private ProductClient productClient;

    private OrderItemMapper orderItemMapper = new OrderItemMapper();
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository,
                orderMapper,
                sellerOrderRepository,
                orderItemRepository,
                shipmentRepository,
                discountAllocationService,
                productClient,
                orderItemMapper
        );
    }

    @Test
    @DisplayName("Checkout should split items by Store and create SellerOrders and OrderItems")
    void testCheckoutMultiStoreSplit() {
        // Prepare items: 2 items from store-1, 1 item from store-2
        CheckoutItemRequest item1 = new CheckoutItemRequest("p1", "v1", "store-1", "Áo Polo", "SKU1", "img1", "L", 2, 200000L, "ref1", "live1", Map.of());
        CheckoutItemRequest item2 = new CheckoutItemRequest("p2", "v2", "store-1", "Quần Jeans", "SKU2", "img2", "32", 1, 300000L, null, null, Map.of());
        CheckoutItemRequest item3 = new CheckoutItemRequest("p3", "v3", "store-2", "Giày Sneaker", "SKU3", "img3", "42", 1, 500000L, "ref2", null, Map.of());

        CheckoutRequest request = new CheckoutRequest(
                "cust-123",
                "IDEM_TEST_001",
                "Nguyen Van A",
                "0901234567",
                "a@example.com",
                "123 Nguyen Hue, Q1, HCMC",
                "COD",
                "VND",
                null,
                0L,
                null,
                "Giao gio hanh chinh",
                Map.of(),
                List.of(item1, item2, item3)
        );

        when(orderRepository.findByIdempotencyKey("IDEM_TEST_001")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getOrderId() == null) o.setOrderId("order-uuid-1");
            return o;
        });

        when(sellerOrderRepository.save(any(SellerOrder.class))).thenAnswer(invocation -> {
            SellerOrder so = invocation.getArgument(0);
            if (so.getSellerOrderId() == null) so.setSellerOrderId("so-" + UUID.randomUUID().toString().substring(0, 5));
            return so;
        });

        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem oi = invocation.getArgument(0);
            if (oi.getOrderItemId() == null) oi.setOrderItemId("oi-" + UUID.randomUUID().toString().substring(0, 5));
            return oi;
        });

        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment s = invocation.getArgument(0);
            if (s.getShipmentId() == null) s.setShipmentId("ship-" + UUID.randomUUID().toString().substring(0, 5));
            return s;
        });

        // Act
        CheckoutResponse response = orderService.checkout(request);

        // Assert
        assertNotNull(response);
        assertEquals("cust-123", response.customerId());
        assertEquals("IDEM_TEST_001", response.idempotencyKey());
        assertEquals(OrderStatus.PENDING, response.status());

        // Total: 2*200k + 1*300k + 1*500k = 1.200.000 VND
        assertEquals(1200000L, response.payableVnd());
        assertEquals(1200000L, response.totalGrossVnd());
        assertEquals(0L, response.totalDiscountVnd());

        // Verify SellerOrders saved (initial create + totals update for 2 unique stores)
        verify(sellerOrderRepository, times(4)).save(any(SellerOrder.class));
        // Verify 3 OrderItems created
        verify(orderItemRepository, times(3)).save(any(OrderItem.class));
        // Verify 2 Shipments created (1 per SellerOrder)
        verify(shipmentRepository, times(2)).save(any(Shipment.class));
    }

    @Test
    @DisplayName("Checkout should return existing order if idempotency key already exists")
    void testCheckoutIdempotency() {
        Order existing = Order.builder()
                .orderId("order-existing")
                .customerId("cust-999")
                .idempotencyKey("IDEM_EXISTS")
                .payableVnd(500000L)
                .status(OrderStatus.PENDING)
                .currency("VND")
                .build();

        when(orderRepository.findByIdempotencyKey("IDEM_EXISTS")).thenReturn(Optional.of(existing));
        when(sellerOrderRepository.findByOrderId("order-existing")).thenReturn(Collections.emptyList());

        CheckoutItemRequest item = new CheckoutItemRequest("p1", null, "store-1", "Áo", "SKU", "img", null, 1, 500000L, null, null, Map.of());
        CheckoutRequest request = new CheckoutRequest(
                "cust-999", "IDEM_EXISTS", "Nam", "0900000000", "n@test.com", "Hanoi", "COD", "VND", null, 0L, null, null, null, List.of(item)
        );

        CheckoutResponse response = orderService.checkout(request);

        assertNotNull(response);
        assertEquals("order-existing", response.orderId());
        assertEquals("IDEM_EXISTS", response.idempotencyKey());

        // Should NOT create new records
        verify(orderRepository, never()).save(any(Order.class));
        verify(sellerOrderRepository, never()).save(any(SellerOrder.class));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
    }
}
