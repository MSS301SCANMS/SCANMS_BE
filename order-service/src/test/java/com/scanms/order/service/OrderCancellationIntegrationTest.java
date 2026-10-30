package com.scanms.order.service;

import com.scanms.order.constant.*;
import com.scanms.order.dto.request.*;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:cancellation-orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.finance.client-secret=", "logging.level.root=WARN"})
class OrderCancellationIntegrationTest {
    @Autowired
    OrderCancellationService service;
    @Autowired
    CancellationRefundSource refundSource;
    @Autowired
    CommerceLifecycle commerce;
    @Autowired
    OrderRepository orders;
    @Autowired
    SellerOrderRepository sellers;
    @Autowired
    OrderItemRepository items;
    @Autowired
    ReturnRequestRepository returns;
    @Autowired
    ShipmentRepository shipments;
    @Autowired
    PublicProductSales sales;
    @MockitoBean
    CommerceAccess access;
    @MockitoBean
    JwtDecoder decoder;
    private final Jwt buyer = jwt("buyer"), shop = jwt("shop"), payment = jwt("payment"), stranger = jwt("stranger");

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue(subject).header("alg", "RS256").subject(subject).build();
    }

    @BeforeEach
    void setup() {
        returns.deleteAll();
        shipments.deleteAll();
        items.deleteAll();
        sellers.deleteAll();
        orders.deleteAll();
        when(access.customer(any())).thenAnswer(call -> ((Jwt) call.getArgument(0)).getSubject());
        when(access.role(any(), anyString())).thenAnswer(call -> "payment".equals(((Jwt) call.getArgument(0)).getSubject()) && "PAYMENT_INTERNAL".equals(call.getArgument(1)));
    }

    private Order order(SellerOrderStatus status, int stores) {
        var order = orders.saveAndFlush(Order.builder().customerId("buyer").idempotencyKey(UUID.randomUUID().toString()).payableVnd(200L).currency("VND")
                .status(OrderStatus.PAID).sagaState(Map.of("paymentId", "payment-id", "paymentMethod", "WALLET", "checkoutState", "READY"))
                .checkoutRequest("{}").shippingSnapshot(Map.of()).build());
        for (int index = 0; index < stores; index++)
            sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId("store-" + index).status(status).totalsSnapshot(Map.of()).build());
        return order;
    }

    @Test
    void ownerCancelsPendingOrderOnceAndOnlyPaymentCanAcknowledgeExactRefund() {
        var order = order(SellerOrderStatus.PENDING, 1);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.request(order.getOrderId(), new OrderCancellationRequest("reason"), stranger)).getStatusCode().value());
        var result = service.request(order.getOrderId(), new OrderCancellationRequest("Đổi địa chỉ"), buyer);
        assertEquals("CANCELLED", result.get("state"));
        assertEquals("PENDING", result.get("refundStatus"));
        assertEquals(result.get("cancelledAt"), service.request(order.getOrderId(), new OrderCancellationRequest("retry"), buyer).get("cancelledAt"));
        assertEquals(OrderStatus.CANCELLED, orders.findById(order.getOrderId()).orElseThrow().getStatus());
        assertTrue(sellers.findByOrderId(order.getOrderId()).stream().allMatch(row -> row.getStatus() == SellerOrderStatus.CANCELLED));
        var refund = new OrderCancellationRefund("payment-id", "ledger", 200L);
        assertThrows(ResponseStatusException.class, () -> refundSource.acknowledge(order.getOrderId(), refund, buyer));
        assertThrows(ResponseStatusException.class, () -> refundSource.acknowledge(order.getOrderId(), new OrderCancellationRefund("payment-id", "ledger", 201L), payment));
        assertEquals("REFUNDED", refundSource.acknowledge(order.getOrderId(), refund, payment).get("refundStatus"));
        assertEquals("ledger", refundSource.acknowledge(order.getOrderId(), refund, payment).get("refundReference"));
        assertThrows(ResponseStatusException.class, () -> refundSource.acknowledge(order.getOrderId(), new OrderCancellationRefund("payment-id", "another-ledger", 200L), payment));
    }

    @Test
    void confirmedMultiStoreOrderWaitsForAllShopsAndBlocksDispatchDuringRequest() {
        var order = order(SellerOrderStatus.CONFIRMED, 2);
        var rows = sellers.findByOrderId(order.getOrderId());
        assertEquals("REQUESTED", service.request(order.getOrderId(), new OrderCancellationRequest("Không còn nhu cầu"), buyer).get("state"));
        assertThrows(ResponseStatusException.class, () -> commerce.dispatch(rows.getFirst().getSellerOrderId(), new CommerceLifecycle.Dispatch("MANUAL", "Carrier", "TRACK"), shop));
        assertEquals("REQUESTED", service.decide(rows.getFirst().getSellerOrderId(), new OrderCancellationDecision(true, ""), shop).get("state"));
        assertEquals(OrderStatus.PAID, orders.findById(order.getOrderId()).orElseThrow().getStatus());
        assertEquals("CANCELLED", service.decide(rows.getLast().getSellerOrderId(), new OrderCancellationDecision(true, ""), shop).get("state"));
    }

    @Test
    void rejectionResumesFulfillmentAndShippedOrderCannotBeCancelled() {
        var order = order(SellerOrderStatus.CONFIRMED, 1);
        var seller = sellers.findByOrderId(order.getOrderId()).getFirst();
        service.request(order.getOrderId(), new OrderCancellationRequest("Đổi địa chỉ"), buyer);
        assertThrows(ResponseStatusException.class, () -> service.decide(seller.getSellerOrderId(), new OrderCancellationDecision(false, ""), shop));
        assertEquals("REJECTED", service.decide(seller.getSellerOrderId(), new OrderCancellationDecision(false, "Đã chuẩn bị theo yêu cầu riêng"), shop).get("state"));
        commerce.dispatch(seller.getSellerOrderId(), new CommerceLifecycle.Dispatch("MANUAL", "Carrier", "TRACK"), shop);
        assertThrows(ResponseStatusException.class, () -> service.request(order.getOrderId(), new OrderCancellationRequest("Hủy"), buyer));
        assertEquals(1, shipments.count());
    }

    @Test
    void concurrentCancellationAndHandoverCannotBothSucceed() throws Exception {
        var order = order(SellerOrderStatus.PENDING, 1);
        String seller = sellers.findByOrderId(order.getOrderId()).getFirst().getSellerOrderId();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var cancel = executor.submit(() -> {
                start.await();
                try {
                    service.request(order.getOrderId(), new OrderCancellationRequest("Hủy"), buyer);
                    return true;
                } catch (ResponseStatusException ex) {
                    return false;
                }
            });
            var dispatch = executor.submit(() -> {
                start.await();
                try {
                    commerce.dispatch(seller, new CommerceLifecycle.Dispatch("MANUAL", "Carrier", "TRACK"), shop);
                    return true;
                } catch (ResponseStatusException ex) {
                    return false;
                }
            });
            start.countDown();
            assertNotEquals(cancel.get(20, TimeUnit.SECONDS), dispatch.get(20, TimeUnit.SECONDS));
            boolean cancelled = orders.findById(order.getOrderId()).orElseThrow().getStatus() == OrderStatus.CANCELLED;
            assertEquals(cancelled ? 0 : 1, shipments.count());
        }
    }

    @Test
    void publicSoldCountIncludesDeliveredUnitsMinusRefundedUnitsOnly() {
        var paid = order(SellerOrderStatus.DELIVERED, 1);
        var seller = sellers.findByOrderId(paid.getOrderId()).getFirst();
        var item = items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).productId("product").quantity(3).build());
        var pending = order(SellerOrderStatus.PENDING, 1);
        items.saveAndFlush(OrderItem.builder().sellerOrderId(sellers.findByOrderId(pending.getOrderId()).getFirst().getSellerOrderId()).productId("product").quantity(100).build());
        returns.saveAndFlush(ReturnRequest.builder().orderItemId(item.getOrderItemId()).quantity(1).status(ReturnRequestStatus.REFUNDED).build());
        assertEquals(Map.of("product", 2L, "other", 0L), sales.soldQuantities(List.of("product", "other")));
    }
}
