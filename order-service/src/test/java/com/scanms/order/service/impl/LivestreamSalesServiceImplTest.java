package com.scanms.order.service.impl;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;

class LivestreamSalesServiceImplTest {
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final SellerOrderRepository sellers = mock(SellerOrderRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LivestreamSalesServiceImpl service = new LivestreamSalesServiceImpl(items, sellers, orders, builder);
    private final Jwt owner = Jwt.withTokenValue("owner-token").header("alg", "none")
            .subject("subject-1").claim("realm_access", Map.of("roles", List.of("SHOP_OWNER"))).build();

    @BeforeEach void setUrls() {
        ReflectionTestUtils.setField(service, "productUrl", "http://product.test");
        ReflectionTestUtils.setField(service, "userUrl", "http://user.test");
    }

    private void expectSources(String ownerId) {
        server.expect(once(), requestTo("http://product.test/api/v1/livestreams/live-1"))
                .andRespond(withSuccess("{\"result\":{\"storeId\":\"store-1\"}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://user.test/api/v1/users/me"))
                .andRespond(withSuccess("{\"result\":{\"status\":\"ACTIVE\",\"identitySubject\":\"subject-1\",\"userId\":\"owner-1\"}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://product.test/api/v1/stores/store-1"))
                .andRespond(withSuccess("{\"result\":{\"storeId\":\"store-1\",\"ownerUserId\":\"" + ownerId + "\"}}", MediaType.APPLICATION_JSON));
    }

    @Test void countsOnlyPaidAttributedSalesForThisStore() {
        expectSources("owner-1");
        when(items.findBySourceLivestreamId("live-1")).thenReturn(List.of(
                line("seller-1", 2, 200_000, 180_000), line("seller-1", 1, 100_000, 90_000),
                line("seller-2", 1, 50_000, 50_000), line("seller-3", 1, 70_000, 70_000)));
        when(sellers.findAllById(any())).thenReturn(List.of(
                seller("seller-1", "store-1", "order-1"), seller("seller-2", "store-1", "order-2"),
                seller("seller-3", "other-store", "order-3")));
        when(orders.findAllById(any())).thenReturn(List.of(
                order("order-1", "buyer-1", OrderStatus.PAID),
                order("order-2", "buyer-2", OrderStatus.AWAITING_PAYMENT),
                order("order-3", "buyer-3", OrderStatus.PAID)));

        var result = service.summary("live-1", owner);

        assertEquals(1, result.buyerCount());
        assertEquals(1, result.orderCount());
        assertEquals(3, result.soldQuantity());
        assertEquals(300_000, result.gmvVnd());
        assertEquals(270_000, result.netSalesVnd());
        server.verify();
    }

    @Test void anotherStoreOwnerCannotReadSales() {
        expectSources("another-owner");

        assertThrows(ResponseStatusException.class, () -> service.summary("live-1", owner));
        verifyNoInteractions(items, sellers, orders);
        server.verify();
    }

    @Test void tokenWithoutUserProfileCannotReadStoreRevenue() {
        server.expect(once(), requestTo("http://user.test/api/v1/users/me"))
                .andRespond(withBadRequest());

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.storeSummary("store-1", owner));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(items, sellers, orders);
        server.verify();
    }

    @Test void storeSummaryCountsOrdersButOnlyPaidItemsAsRevenue() {
        server.expect(once(), requestTo("http://user.test/api/v1/users/me"))
                .andRespond(withSuccess("{\"result\":{\"status\":\"ACTIVE\",\"identitySubject\":\"subject-1\",\"userId\":\"owner-1\"}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://product.test/api/v1/stores/store-1"))
                .andRespond(withSuccess("{\"result\":{\"storeId\":\"store-1\",\"ownerUserId\":\"owner-1\"}}", MediaType.APPLICATION_JSON));
        when(sellers.findByStoreIdOrderByCreatedAtDesc("store-1")).thenReturn(List.of(
                seller("seller-1", "store-1", "order-1"), seller("seller-2", "store-1", "order-2")));
        when(orders.findAllById(any())).thenReturn(List.of(
                order("order-1", "buyer-1", OrderStatus.PAID),
                order("order-2", "buyer-2", OrderStatus.AWAITING_PAYMENT)));
        when(items.findBySellerOrderIdIn(any())).thenReturn(List.of(line("seller-1", 2, 200_000, 180_000)));

        var result = service.storeSummary("store-1", owner);

        assertEquals(2, result.orderCount());
        assertEquals(1, result.paidOrderCount());
        assertEquals(180_000, result.paidRevenueVnd());
        server.verify();
    }

    private static OrderItem line(String sellerId, int quantity, long gross, long net) {
        return OrderItem.builder().sellerOrderId(sellerId).quantity(quantity)
                .grossAmountVnd(gross).netPaidAmountVnd(net).build();
    }

    private static SellerOrder seller(String id, String storeId, String orderId) {
        return SellerOrder.builder().sellerOrderId(id).storeId(storeId).orderId(orderId)
                .status(SellerOrderStatus.PENDING).build();
    }

    private static Order order(String id, String customerId, OrderStatus status) {
        return Order.builder().orderId(id).customerId(customerId).status(status).build();
    }
}
