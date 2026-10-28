package com.scanms.product.controller;

import com.scanms.product.constant.*;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.ProductAccess;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:verified-feedback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN"})
class VerifiedFeedbackIntegrationTest {
    @Autowired
    WebApplicationContext context;
    @Autowired
    FeedbackRepository feedbacks;
    @Autowired
    ProductRepository products;
    @Autowired
    StoreRepository stores;
    @MockitoBean
    ProductAccess access;
    @MockitoBean
    JwtDecoder decoder;
    MockMvc mvc;
    Store store;
    Product product;
    static volatile String productId, storeId, paid = "PAID", delivered = "COMPLETED";
    static final HttpServer source = startSource();

    static HttpServer startSource() {
        try {
            var s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/api/v1/", exchange -> {
                Map<String, Object> result = switch (exchange.getRequestURI().getPath()) {
                    case "/api/v1/order-items/item-1" -> Map.of("productId", productId, "sellerOrderId", "seller-1");
                    case "/api/v1/seller-orders/seller-1" ->
                            Map.of("orderId", "order-1", "storeId", storeId, "status", delivered);
                    default -> Map.of("customerId", "buyer-1", "status", paid);
                };
                byte[] bytes = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("result", result)).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            s.start();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("clients.order.url", () -> "http://127.0.0.1:" + source.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        source.stop(0);
    }

    @BeforeEach
    void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        feedbacks.deleteAll();
        products.deleteAll();
        stores.deleteAll();
        paid = "PAID";
        delivered = "COMPLETED";
        store = stores.saveAndFlush(Store.builder().name("Test shop").ownerUserId("owner-1").approvalStatus(StoreStatus.APPROVED).build());
        storeId = store.getStoreId();
        product = products.saveAndFlush(Product.builder().name("Test shirt").storeId(storeId).status(ProductStatus.ACTIVE).priceVnd(269000L).build());
        productId = product.getProductId();
        when(access.userId()).thenReturn("buyer-1");
        when(access.bearerToken()).thenReturn("fixture-buyer-token");
    }

    Map<String, Object> payload(String comment) {
        return new LinkedHashMap<>(Map.of("productId", productId, "orderItemId", "item-1", "customerId", "forged-customer", "rating", 5, "comment", comment, "imageRefs", List.of("https://assets.example.test/review.jpg")));
    }

    @Test
    void profanityIsRejectedOnCreateAndEditWhileNegativeReviewsAreAllowed() throws Exception {
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(json(payload("f.u.c.k")))).andExpect(status().isBadRequest());
        assertEquals(0, feedbacks.count());
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(json(payload("Áo quá lớn, chất lượng tệ")))).andExpect(status().isCreated());
        var feedback = feedbacks.findAll().getFirst();
        mvc.perform(patch("/api/v1/feedbacks/" + feedback.getFeedbackId()).with(jwt()).contentType("application/json").content(json(Map.of("rating", 1, "comment", "fuck")))).andExpect(status().isBadRequest());
        var unchanged = feedbacks.findById(feedback.getFeedbackId()).orElseThrow();
        assertEquals(5, unchanged.getRating());
        assertEquals("Áo quá lớn, chất lượng tệ", unchanged.getComment());
    }

    String json(Object value) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);
    }

    @Test
    void verifiedReviewPersistsLongTextAndImagesAndAppearsOnLandingWithoutPrivateIds() throws Exception {
        String comment = "Áo đẹp và đúng mô tả. ".repeat(30);
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(json(payload(comment))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.result.customerId").value("buyer-1")).andExpect(jsonPath("$.result.imageRefs[0]").value("https://assets.example.test/review.jpg"));
        assertEquals(comment, feedbacks.findAll().getFirst().getComment());
        assertEquals(1L, stores.findById(storeId).orElseThrow().getRatingCount());
        mvc.perform(get("/api/v1/public/products/" + productId + "/landing")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reviews.totalReviews").value(1)).andExpect(jsonPath("$.result.reviews.averageRating").value(5))
                .andExpect(jsonPath("$.result.reviews.items[0].isVerifiedBuyer").value(true)).andExpect(jsonPath("$.result.reviews.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.result.reviews.items[0].orderItemId").doesNotExist());
        mvc.perform(get("/api/v1/feedbacks/mine").with(jwt()).param("orderItemIds", "item-1")).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].orderItemId").value("item-1"));
        when(access.userId()).thenReturn("buyer-2");
        mvc.perform(get("/api/v1/feedbacks/mine").with(jwt()).param("orderItemIds", "item-1")).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void malformedImageObjectMissingItemAndExcessMediaReturn400InsteadOf500() throws Exception {
        var bad = payload("Áo đẹp đúng mô tả");
        bad.put("imageRefs", Map.of("images", List.of()));
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(json(bad))).andExpect(status().isBadRequest());
        bad = payload("Áo đẹp đúng mô tả");
        bad.remove("orderItemId");
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(json(bad))).andExpect(status().isBadRequest());
        bad = payload("Áo đẹp đúng mô tả");
        bad.put("imageRefs", Collections.nCopies(6, "https://assets.example.test/a.jpg"));
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(json(bad))).andExpect(status().isBadRequest());
        assertEquals(0, feedbacks.count());
    }

    @Test
    void homepageRatingsArePerProductAndExcludeHiddenOrRemovedReviews() throws Exception {
        var other = products.saveAndFlush(Product.builder().name("Unreviewed product").storeId(storeId).status(ProductStatus.ACTIVE).priceVnd(100000L).build());
        var first = feedbacks.saveAndFlush(Feedback.builder().productId(productId).orderItemId("rated-item-1").customerId("buyer-1").rating(3).comment("Verified review").status(FeedbackStatus.ACTIVE).build());
        feedbacks.saveAndFlush(Feedback.builder().productId(productId).orderItemId("rated-item-2").customerId("buyer-2").rating(5).status(FeedbackStatus.ACTIVE).build());
        feedbacks.saveAndFlush(Feedback.builder().productId(productId).orderItemId("hidden-item").customerId("buyer-3").rating(1).status(FeedbackStatus.HIDDEN).build());
        feedbacks.saveAndFlush(Feedback.builder().productId(productId).orderItemId("removed-item").customerId("buyer-4").rating(1).status(FeedbackStatus.REMOVED).build());
        var rows = tools.jackson.databind.json.JsonMapper.builder().build().readTree(mvc.perform(get("/api/v1/public/products")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("result").path("items");
        var rated = java.util.stream.StreamSupport.stream(rows.spliterator(), false).filter(p -> p.path("id").asText().equals(productId)).findFirst().orElseThrow();
        var empty = java.util.stream.StreamSupport.stream(rows.spliterator(), false).filter(p -> p.path("id").asText().equals(other.getProductId())).findFirst().orElseThrow();
        assertEquals(4.0, rated.path("rating").asDouble());
        assertEquals(2, rated.path("reviewCount").asLong());
        assertTrue(empty.path("rating").isNull());
        assertEquals(0, empty.path("reviewCount").asLong());
        assertFalse(rated.has("customerId"));
        mvc.perform(patch("/api/v1/feedbacks/" + first.getFeedbackId()).with(jwt()).contentType("application/json").content("{\"rating\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/products/" + productId)).andExpect(status().isOk()).andExpect(jsonPath("$.result.rating").value(3)).andExpect(jsonPath("$.result.reviewCount").value(2));
        mvc.perform(delete("/api/v1/feedbacks/" + first.getFeedbackId()).with(jwt())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/products/" + productId)).andExpect(status().isOk()).andExpect(jsonPath("$.result.rating").value(5)).andExpect(jsonPath("$.result.reviewCount").value(1));
    }

    @Test
    void onlyDeliveredPaidBuyerMayReviewAndDuplicateCannotIncreaseRatings() throws Exception {
        var body = json(payload("Áo đẹp đúng mô tả"));
        mvc.perform(post("/api/v1/feedbacks").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        when(access.userId()).thenReturn("another-buyer");
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        when(access.userId()).thenReturn("buyer-1");
        paid = "AWAITING_PAYMENT";
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        paid = "PAID";
        delivered = "SHIPPED";
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        delivered = "COMPLETED";
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/feedbacks").with(jwt()).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertEquals(1, feedbacks.count());
        assertEquals(1L, stores.findById(storeId).orElseThrow().getRatingCount());
        var f = feedbacks.findAll().getFirst();
        f.setStatus(FeedbackStatus.HIDDEN);
        feedbacks.saveAndFlush(f);
        mvc.perform(get("/api/v1/public/products/" + productId + "/landing")).andExpect(status().isOk()).andExpect(jsonPath("$.result.reviews.totalReviews").value(0));
    }
}
