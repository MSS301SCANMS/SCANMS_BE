package com.scanms.product.controller;

import com.scanms.product.client.ChatCollaborationClient;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Store;
import com.scanms.product.entity.Product;
import com.scanms.product.constant.ProductStatus;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:store-chat-web;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.catalog.seed-enabled=false", "scanms.catalog.cache-enabled=false", "logging.level.root=WARN"})
class StoreChatWebIntegrationTest {
    @Autowired StoreChatService chat;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;
    @Autowired StoreChatMessageRepository messages;
    @Autowired StoreChatConversationRepository conversations;
    @MockitoBean ProductAccess access;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ChatCollaborationClient collaboration;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    private final ThreadLocal<String> actor = ThreadLocal.withInitial(() -> "buyer");
    String storeId;

    @BeforeEach void setup() {
        messages.deleteAll(); conversations.deleteAll(); products.deleteAll(); stores.deleteAll();
        actor.set("buyer");
        when(access.userId()).thenAnswer(call -> actor.get());
        when(access.bearerToken()).thenReturn("fixture");
        storeId = stores.saveAndFlush(Store.builder().name("Web Shop").ownerUserId("owner").approvalStatus(StoreStatus.APPROVED).build()).getStoreId();
    }

    @Test void persistsRepliesPaginationUnreadAndIdempotentRetries() {
        String id = chat.open(storeId).get("id").toString();
        assertEquals(id, chat.open(storeId).get("id"));
        for (int i = 0; i < 55; i++) chat.send(id, "Message " + i, UUID.randomUUID().toString());
        var latest = chat.history(id, 50, null);
        assertEquals(50, latest.size());
        assertEquals(5, chat.history(id, 50, latest.getFirst().getId()).size());
        var sent = latest.getLast();
        assertEquals(sent.getId(), chat.send(id, sent.getMessageText(), sent.getId()).getId());
        assertEquals(55, messages.count());
        actor.set("owner");
        assertEquals(55L, ((Map<?, ?>) chat.list().getFirst().get("_count")).get("chatMessages"));
        assertEquals(true, chat.list().getFirst().get("isStoreOwner"));
        chat.read(id);
        assertEquals(0L, ((Map<?, ?>) chat.list().getFirst().get("_count")).get("chatMessages"));
        chat.send(id, "Shop reply", UUID.randomUUID().toString());
        actor.set("buyer");
        assertEquals("Shop reply", chat.history(id, 1, null).getFirst().getMessageText());
        assertEquals(List.of("buyer", "owner"), chat.subscription(id).get("participantIds"));
    }

    @Test void strangerCannotSubscribeReadSendOrSpoofCustomer() {
        String id = chat.open(storeId).get("id").toString();
        actor.set("outsider");
        assertThrows(ResponseStatusException.class, () -> chat.subscription(id));
        assertThrows(ResponseStatusException.class, () -> chat.history(id, 50, null));
        assertThrows(ResponseStatusException.class, () -> chat.send(id, "forged", UUID.randomUUID().toString()));
        assertThrows(ResponseStatusException.class, () -> chat.read(id));
        assertThrows(ResponseStatusException.class, () -> chat.open(storeId, "buyer"));
        assertTrue(chat.list().isEmpty());
    }

    @Test void ownerStartsChatOnlyWithVerifiedPartnerAndKeepsTheRealPartnerName() {
        actor.set("owner");
        when(collaboration.partner(anyString(), eq(storeId), eq("kol")))
                .thenReturn(Map.of("result", Map.of("storeId", storeId, "customerId", "kol", "name", "Approved Creator")));
        var opened = chat.open(storeId, "kol");
        assertEquals("Approved Creator", ((Map<?, ?>) opened.get("customer")).get("fullName"));
        assertThrows(ResponseStatusException.class, () -> chat.open(storeId, "unrelated-user"));
        actor.set("kol");
        assertEquals(opened.get("id"), chat.open(storeId).get("id"));
    }

    @Test void productContextUsesCatalogSnapshotAndRetryKeepsItsIdentityAfterPriceChange() {
        var product = products.saveAndFlush(Product.builder().name("Catalog Shirt").storeId(storeId).priceVnd(100000L).status(ProductStatus.ACTIVE).build());
        String id = chat.open(storeId).get("id").toString(), key = UUID.randomUUID().toString();
        String content = "{\"type\":\"PRODUCT_INQUIRY\",\"productId\":\"" + product.getProductId() + "\",\"productTitle\":\"Forged title\",\"productPrice\":1}";
        var sent = chat.send(id, content, key);
        assertTrue(sent.getMessageText().contains("Catalog Shirt"));
        assertFalse(sent.getMessageText().contains("Forged title"));
        product.setPriceVnd(120000L); products.saveAndFlush(product);
        assertEquals(sent.getMessageText(), chat.send(id, content, key).getMessageText());
        assertEquals(1, messages.count());
    }

    @Test void concurrentOpenAndSendCreateOneConversationAndOneMessage() throws Exception {
        String key = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<String>>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> {
                String id = chat.open(storeId).get("id").toString();
                chat.send(id, "retry", key);
                return id;
            }));
            var ids = new HashSet<String>();
            for (var task : tasks) ids.add(task.get(20, TimeUnit.SECONDS));
            assertEquals(1, ids.size()); assertEquals(1, conversations.count()); assertEquals(1, messages.count());
        }
    }

    @Test void collaborationCardsAreVerifiedAgainstTheirConversation() {
        String id = chat.open(storeId).get("id").toString();
        when(collaboration.resource(anyString(), eq("COMMISSION_DEAL"), eq("deal")))
                .thenReturn(Map.of("result", Map.of("customerId", "another-kol", "storeId", storeId)));
        assertThrows(ResponseStatusException.class, () -> chat.send(id, "{\"type\":\"COMMISSION_DEAL\",\"resourceId\":\"deal\"}", UUID.randomUUID().toString()));
        assertEquals(0, messages.count());
        when(collaboration.resource(anyString(), eq("COMMISSION_DEAL"), eq("deal")))
                .thenReturn(Map.of("result", Map.of("customerId", "buyer", "storeId", storeId)));
        assertTrue(chat.send(id, "{\"type\":\"COMMISSION_DEAL\",\"resourceId\":\"deal\"}", UUID.randomUUID().toString()).getMessageText().contains("resourceId"));
    }

    @Test void httpRequiresLoginAndReturnsOnlyAuthorizedHistory() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/conversations"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        String id = chat.open(storeId).get("id").toString();
        String payload = "{\"messageText\":\"HTTP hello\",\"messageId\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/chat/conversations/" + id + "/messages")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                        .contentType("application/json").content(payload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.senderId").value("buyer"));
        actor.set("outsider");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/conversations/" + id + "/subscription")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
