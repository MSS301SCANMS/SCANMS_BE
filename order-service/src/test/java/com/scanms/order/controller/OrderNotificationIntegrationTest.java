package com.scanms.order.controller;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.constant.ShipmentStatus;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.entity.Shipment;
import com.scanms.order.repository.OrderNotificationReadRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.CommerceAccess;
import com.scanms.order.service.CommerceLifecycle;
import com.scanms.order.service.OrderNotificationService;
import com.scanms.order.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:order-notifications;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret=",
        "scanms.shipping.simulation-enabled=true"})
@ActiveProfiles("local")
class OrderNotificationIntegrationTest {
    @Autowired OrderNotificationService notices;
    @Autowired OrderNotificationReadRepository reads;
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired ShipmentRepository shipmentRows;
    @Autowired ShipmentService shipmentService;
    @Autowired CommerceLifecycle lifecycle;
    @Autowired WebApplicationContext context;
    @MockitoBean CommerceAccess access;
    @MockitoBean JwtDecoder decoder;
    final Jwt buyer = token("buyer"), stranger = token("stranger");

    @BeforeEach void setup() {
        reads.deleteAll(); shipmentRows.deleteAll(); sellers.deleteAll(); orders.deleteAll();
        when(access.customer(any())).thenAnswer(call -> ((Jwt) call.getArgument(0)).getSubject());
        when(access.stores(any())).thenReturn(Set.of());
    }

    @Test void simulationAndShipmentCallbackExposeOneDurableDeliveryNotice() {
        var seller = seller("buyer", null);
        var shipment = shipmentRows.saveAndFlush(Shipment.builder().sellerOrderId(seller.getSellerOrderId())
                .environment("TEST").status(ShipmentStatus.SHIPPED).trackingCode("SIM-QA").build());
        assertEquals(0, notices.list("ORDER", 1, 20, buyer).total());
        lifecycle.simulate(seller.getSellerOrderId(), true, buyer);
        lifecycle.simulate(seller.getSellerOrderId(), true, buyer);
        shipmentService.updateShipmentStatus(shipment.getShipmentId(), ShipmentStatus.DELIVERED, null, null, null);
        var page = notices.list("ORDER", 1, 20, buyer);
        assertEquals(1, page.total()); assertEquals(1, page.unreadCount());
        var item = page.items().getFirst();
        assertEquals("ORDER_DELIVERED", item.type());
        assertEquals(seller.getOrderId(), item.data().get("orderId"));
        assertTrue(item.data().get("actionUrl").toString().contains(seller.getOrderId()));
        assertFalse(item.isRead()); assertTrue(item.message().contains("xác nhận đã nhận hàng"));
        assertEquals(sellers.findById(seller.getSellerOrderId()).orElseThrow().getDeliveredAt().toInstant(java.time.ZoneOffset.UTC), item.createdAt());
        var saved = sellers.findById(seller.getSellerOrderId()).orElseThrow();
        saved.setStatus(SellerOrderStatus.COMPLETED); sellers.saveAndFlush(saved);
        assertEquals(item.id(), notices.list("ALL", 1, 20, buyer).items().getFirst().id());
    }

    @Test void historicalOrdersAreIncludedAndReadReceiptsRemainScopedToTheBuyer() {
        seller("buyer", LocalDateTime.of(2026, 10, 26, 12, 0));
        seller("stranger", LocalDateTime.of(2026, 10, 27, 12, 0));
        String id = notices.list("ORDER", 1, 20, buyer).items().getFirst().id();
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> notices.markRead(id, stranger)).getStatusCode().value());
        notices.markRead(id, buyer); notices.markRead(id, buyer);
        assertEquals(1, reads.count()); assertEquals(0, notices.list("ALL", 1, 20, buyer).unreadCount());
        assertTrue(notices.list("ALL", 1, 20, buyer).items().getFirst().isRead());
        assertEquals(1, notices.list("ALL", 1, 20, stranger).unreadCount());
    }

    @Test void paginationCategoriesAndReadAllDoNotHideLaterDeliveries() {
        for (int i = 0; i < 3; i++) seller("buyer", LocalDateTime.of(2026, 11, 1, 12, i));
        var first = notices.list("ALL", 1, 2, buyer);
        var second = notices.list("ALL", 2, 2, buyer);
        assertEquals(3, first.total()); assertEquals(2, first.totalPages()); assertEquals(1, second.items().size());
        assertNotEquals(first.items().getFirst().id(), second.items().getFirst().id());
        assertEquals(0, notices.list("PROMOTION", 1, 20, buyer).items().size());
        assertEquals(3L, notices.list("SYSTEM", 1, 20, buyer).categoryUnreadCounts().get("ORDER"));
        notices.markAllRead(buyer); notices.markAllRead(buyer);
        assertEquals(0L, notices.unread(buyer).get("unreadCount")); assertEquals(3, reads.count());
        seller("buyer", LocalDateTime.of(2026, 11, 2, 12, 0));
        assertEquals(1, notices.list("ORDER", 1, 20, buyer).unreadCount());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> notices.list("FAKE", 1, 20, buyer)).getStatusCode().value());
    }

    @Test void concurrentReadRetriesCreateOneReceipt() throws Exception {
        seller("buyer", LocalDateTime.now());
        String id = notices.list("ORDER", 1, 20, buyer).items().getFirst().id();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> notices.markRead(id, buyer)));
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, reads.count()); assertEquals(0, notices.list("ORDER", 1, 20, buyer).unreadCount());
    }

    @Test void endpointsRequireLoginAndNeverTrustAUserIdParameter() throws Exception {
        seller("buyer", LocalDateTime.now());
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(stranger)).param("userId", "buyer"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.total").value(0));
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items[0].isRead").value(false));
        mvc.perform(patch("/api/v1/notifications/read-all").with(jwt().jwt(buyer))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/unread-count").with(jwt().jwt(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.unreadCount").value(0));
    }

    private SellerOrder seller(String customerId, LocalDateTime deliveredAt) {
        var order = orders.saveAndFlush(Order.builder().customerId(customerId).status(OrderStatus.PAID).payableVnd(299000L).currency("VND").build());
        return sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId("store")
                .status(deliveredAt == null ? SellerOrderStatus.SHIPPED : SellerOrderStatus.DELIVERED)
                .deliveredAt(deliveredAt).returnDeadline(deliveredAt == null ? null : deliveredAt.plusDays(14)).build());
    }

    private static Jwt token(String id) { return Jwt.withTokenValue(id).header("alg", "RS256").subject(id).build(); }
}
