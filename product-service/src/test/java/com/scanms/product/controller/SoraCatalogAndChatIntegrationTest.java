package com.scanms.product.controller;

import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.catalog.cache-enabled=false"})
@EnabledIfEnvironmentVariable(named = "SORA_TEST_POSTGRES_URL", matches = ".+/scanms_qa_sora")
class SoraCatalogAndChatIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("SORA_TEST_POSTGRES_URL");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/scanms_qa_sora"))
            throw new IllegalArgumentException("Only an isolated scanms_qa_sora database is allowed");
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "scanms-qa-only");
    }
    @Autowired SoraCatalogSeedService seed;
    @Autowired StoreChatService chat;
    @Autowired StoreChatConversationRepository conversations;
    @Autowired StoreChatMessageRepository messages;
    @Autowired PublicCatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @MockitoBean ProductAccess access;
    @MockitoBean JwtDecoder decoder;
    private final ThreadLocal<String> actor = ThreadLocal.withInitial(() -> "customer-a");
    private static final String PRODUCT = "a77f64b0-f46d-4c9b-87db-c07f797a2b21";
    private static final String STORE = "02c1461c-aead-4366-9116-f72915bac9ce";
    private static final String OWNER = "3ecf64b8-5c15-4f74-9690-57707ada1104";

    @BeforeEach void setup() throws Exception {
        for (String table : List.of("store_chat_messages", "store_chat_conversations", "product_images", "product_variants", "products", "product_categories", "stores"))
            jdbc.update("DELETE FROM " + table);
        actor.set("customer-a");
        when(access.userId()).thenAnswer(call -> actor.get());
        seed.seed();
    }

    @Test void productIsPublicWithShopImageAndPurchasableSizes() {
        var product = catalog.visibleProducts().getFirst();
        assertEquals(PRODUCT, product.get("id"));
        assertEquals("Sora Skin", ((Map<?,?>)product.get("store")).get("name"));
        assertEquals("/assets/products/sora-caro-shirt.webp", product.get("imageUrl"));
        assertEquals(299000L, product.get("price"));
        assertEquals(100L, product.get("stockQuantity"));
        assertEquals(4, ((List<?>)product.get("variants")).size());
        assertEquals(true, product.get("canPurchase"));
    }

    @Test void repeatSeedDoesNotResetEditedPriceStockOrOwner() throws Exception {
        jdbc.update("UPDATE product_variants SET stock_quantity=7, price_vnd=179000 WHERE sku='SORA-CARO-XANH-M'");
        jdbc.update("UPDATE stores SET owner_user_id='existing-owner', description='Clothing shop' WHERE store_id=?", STORE);
        seed.seed(); seed.seed();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM product_variants", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM product_images", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE sku='SORA-CARO-XANH-M'", Integer.class));
        assertEquals(179000L, jdbc.queryForObject("SELECT price_vnd FROM product_variants WHERE sku='SORA-CARO-XANH-M'", Long.class));
        assertEquals("existing-owner", jdbc.queryForObject("SELECT owner_user_id FROM stores WHERE store_id=?", String.class, STORE));
    }

    @Test void existingSoraShopWithDifferentIdIsReused() throws Exception {
        for (String table : List.of("product_images", "product_variants", "products")) jdbc.update("DELETE FROM " + table);
        String other = UUID.randomUUID().toString();
        jdbc.update("UPDATE stores SET store_id=?, owner_user_id='actual-owner' WHERE store_id=?", other, STORE);
        seed.seed();
        assertEquals(other, jdbc.queryForObject("SELECT store_id FROM products WHERE product_id=?", String.class, PRODUCT));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM stores", Integer.class));
    }

    @Test void buyerAndShopExchangePersistedMessagesAndReadReceipts() {
        String id = chat.open(STORE).get("id").toString();
        assertEquals(id, chat.open(STORE).get("id"));
        String key = UUID.randomUUID().toString();
        var sent = chat.send(id, "Shop has size M?", key);
        assertEquals(sent.getId(), chat.send(id, "Shop has size M?", key).getId());
        assertEquals(1, messages.count());
        assertEquals("customer-a", sent.getSenderId());
        actor.set(OWNER);
        var row = chat.list().getFirst();
        assertEquals(1L, ((Map<?,?>)row.get("_count")).get("chatMessages"));
        assertEquals("Shop has size M?", chat.history(id, 50, null).getFirst().getMessageText());
        chat.read(id);
        assertTrue(chat.history(id, 50, null).getFirst().isRead());
        chat.send(id, "Yes, size M is available", UUID.randomUUID().toString());
        actor.set("customer-a");
        assertEquals(2, chat.history(id, 50, null).size());
        assertTrue(chat.history(id, 1, sent.getId()).isEmpty());
        assertEquals(2, messages.count());
    }

    @Test void unrelatedUserCannotReadSendOrMarkRead() {
        String id = chat.open(STORE).get("id").toString();
        actor.set("stranger");
        assertTrue(chat.list().isEmpty());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> chat.history(id, 50, null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> chat.send(id, "Intrusion", null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> chat.read(id)).getStatusCode().value());
        assertEquals(0, messages.count());
    }

    @Test void httpContractIncludesSenderAndAuthenticationIsRequired() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/conversations"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        String id = chat.open(STORE).get("id").toString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/chat/conversations/" + id + "/messages")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"messageText\":\"Hello Sora\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.sender.id").value("customer-a"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.isRead").value(false));
        actor.set(OWNER);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/conversations/" + id + "/messages")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[0].messageText").value("Hello Sora"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[0].sender.fullName").isNotEmpty());
    }

    @Test void concurrentOpenCreatesOneConversationAndSenderCannotReuseOtherMessageKey() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<String>> results = new ArrayList<>();
            for (int i=0; i<8; i++) results.add(executor.submit(() -> chat.open(STORE).get("id").toString()));
            Set<String> ids = new HashSet<>();
            for (var result : results) ids.add(result.get(15, TimeUnit.SECONDS));
            assertEquals(1, ids.size()); assertEquals(1, conversations.count());
            String id = ids.iterator().next(); String key = UUID.randomUUID().toString();
            chat.send(id, "Buyer message", key);
            actor.set(OWNER);
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> chat.send(id, "Overwrite", key)).getStatusCode().value());
        }
    }
}
