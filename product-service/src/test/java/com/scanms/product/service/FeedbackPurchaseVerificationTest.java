package com.scanms.product.service;

import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.entity.Feedback;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import com.scanms.product.exception.AppException;
import com.scanms.product.mapper.FeedbackMapper;
import com.scanms.product.repository.FeedbackRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.impl.FeedbackServiceImpl;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mapstruct.factory.Mappers;

class FeedbackPurchaseVerificationTest {
    static HttpServer orderSource;

    @BeforeAll static void start() throws Exception {
        orderSource = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/api/v1/order-items/item-1", "{\"result\":{\"productId\":\"product-1\",\"sellerOrderId\":\"seller-1\"}}");
        respond("/api/v1/seller-orders/seller-1", "{\"result\":{\"orderId\":\"order-1\",\"storeId\":\"store-1\",\"status\":\"DELIVERED\"}}");
        respond("/api/v1/orders/order-1", "{\"result\":{\"customerId\":\"buyer-1\",\"status\":\"PAID\"}}");
        respond("/api/v1/order-items/item-2", "{\"result\":{\"productId\":\"product-1\",\"sellerOrderId\":\"seller-2\"}}");
        respond("/api/v1/seller-orders/seller-2", "{\"result\":{\"orderId\":\"order-2\",\"storeId\":\"other-store\",\"status\":\"DELIVERED\"}}");
        respond("/api/v1/orders/order-2", "{\"result\":{\"customerId\":\"buyer-1\",\"status\":\"PAID\"}}");
        respond("/api/v1/order-items/item-3", "{\"result\":{\"productId\":\"product-1\",\"sellerOrderId\":\"seller-3\"}}");
        respond("/api/v1/seller-orders/seller-3", "{\"result\":{\"orderId\":\"order-3\",\"storeId\":\"store-1\",\"status\":\"DELIVERED\"}}");
        respond("/api/v1/orders/order-3", "{\"result\":{\"customerId\":\"buyer-1\",\"status\":\"AWAITING_PAYMENT\"}}");
        orderSource.start();
    }

    private static void respond(String path, String body) {
        orderSource.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @AfterAll static void stop() { orderSource.stop(0); }

    @Test void reviewUsesVerifiedBuyerAndUpdatesActiveStoreRating() {
        var feedbacks = mock(FeedbackRepository.class);
        var products = mock(ProductRepository.class);
        var stores = mock(StoreRepository.class);
        var access = mock(ProductAccess.class);
        var service = new FeedbackServiceImpl(feedbacks, Mappers.getMapper(FeedbackMapper.class), access, products, stores);
        ReflectionTestUtils.setField(service, "orderUrl", "http://127.0.0.1:" + orderSource.getAddress().getPort());
        var product = Product.builder().productId("product-1").storeId("store-1").build();
        var store = Store.builder().storeId("store-1").build();
        when(products.findById("product-1")).thenReturn(Optional.of(product));
        when(products.findByStoreId("store-1")).thenReturn(List.of(product));
        when(stores.findById("store-1")).thenReturn(Optional.of(store));
        when(access.bearerToken()).thenReturn("buyer-token");
        var saved = new AtomicReference<Feedback>();
        when(feedbacks.save(any(Feedback.class))).thenAnswer(call -> { saved.set(call.getArgument(0)); return saved.get(); });
        when(feedbacks.findByProductIdInAndStatus(any(), any())).thenAnswer(call -> List.of(saved.get()));
        var input = new CreateFeedbackRequest("product-1", "item-1", "forged-customer", 5, "Good", List.of(), FeedbackStatus.HIDDEN);

        when(access.userId()).thenReturn("another-buyer");
        assertThrows(AppException.class, () -> service.create(input));
        verify(feedbacks, never()).save(any());

        when(access.userId()).thenReturn("buyer-1");
        var result = service.create(input);
        assertEquals("buyer-1", result.customerId());
        assertEquals(FeedbackStatus.ACTIVE, result.status());
        assertEquals(1L, store.getRatingCount());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(store.getRatingAverage()));
        when(feedbacks.existsByOrderItemIdAndCustomerId("item-1", "buyer-1")).thenReturn(true);
        assertThrows(AppException.class, () -> service.create(input));
        assertThrows(AppException.class, () -> service.create(new CreateFeedbackRequest("product-1", "item-2", null, 5, "Wrong store", List.of(), null)));
        assertThrows(AppException.class, () -> service.create(new CreateFeedbackRequest("product-1", "item-3", null, 5, "Unpaid", List.of(), null)));
        verify(feedbacks, times(1)).save(any());
    }
}
