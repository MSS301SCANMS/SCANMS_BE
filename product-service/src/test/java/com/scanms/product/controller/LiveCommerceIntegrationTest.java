package com.scanms.product.controller;

import com.scanms.product.client.LiveVoucherClient;
import com.scanms.product.client.LivestreamAffiliateClient;
import com.scanms.product.constant.*;
import com.scanms.product.dto.request.*;
import com.scanms.product.entity.*;
import com.scanms.product.exception.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:live-commerce;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.catalog.seed-enabled=false", "scanms.catalog.cache-enabled=false", "LIVE_METRICS_SECRET=fixture-metrics-key-32-characters-long", "logging.level.root=WARN"})
class LiveCommerceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void postgres(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String prefix = System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if (prefix == null || prefix.isBlank()) return;
        if (!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_"))
            throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        properties.add("spring.datasource.url", () -> prefix + "live_commerce");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER", "scanms_qa"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD", ""));
    }

    @Autowired
    LiveCommerceService commerce;
    @Autowired
    LivePurchaseAccess purchases;
    @Autowired
    LivestreamService livestreams;
    @Autowired
    InventoryCheckoutController inventory;
    @Autowired
    ProductRepository products;
    @Autowired
    ProductVariantRepository variants;
    @Autowired
    ProductImageRepository images;
    @Autowired
    StoreRepository stores;
    @Autowired
    LivestreamRepository lives;
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transactions;
    @MockitoBean
    ProductAccess access;
    @MockitoBean
    JwtDecoder decoder;
    @MockitoBean
    LiveVoucherClient vouchers;
    @MockitoBean
    LivestreamAffiliateClient affiliate;
    String storeId, productId, variantId, liveId;
    final Jwt internal = jwt("order-service", "ORDER_INTERNAL");

    Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue(subject).header("alg", "RS256").subject(subject).claim("realm_access", Map.of("roles", List.of(role))).build();
    }

    void actor(String subject) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(subject, "SHOP_OWNER")));
    }

    @BeforeEach
    void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> em.createQuery("delete from CheckoutReservation").executeUpdate());
        lives.deleteAll();
        images.deleteAll();
        variants.deleteAll();
        products.deleteAll();
        stores.deleteAll();
        var store = stores.saveAndFlush(Store.builder().ownerUserId("owner").name("Shop").approvalStatus(StoreStatus.APPROVED).build());
        storeId = store.getStoreId();
        var product = products.saveAndFlush(Product.builder().storeId(storeId).name("Shirt").priceVnd(329000L).status(ProductStatus.ACTIVE).build());
        productId = product.getProductId();
        variantId = variants.saveAndFlush(ProductVariant.builder().productId(productId).sku("WHITE-M").size("M").color("White").priceVnd(329000L).stockQuantity(10).status(ProductVariantStatus.ACTIVE).build()).getVariantId();
        images.saveAndFlush(ProductImage.builder().productId(productId).objectKey("shirt").imageUrl("https://example.test/shirt.webp").primaryImage(true).sortOrder(0).build());
        liveId = lives.saveAndFlush(Livestream.builder().storeId(storeId).title("Live").status(LivestreamStatus.LIVE).productRefs(Map.of("productIds", List.of(productId))).participantRefs(Map.of("broadcast", Map.of("state", "LIVE", "lastHeartbeatAt", Instant.now().toString()))).build()).getLivestreamId();
        when(access.bearerToken()).thenReturn("owner-token");
        doAnswer(call -> {
            if (!"owner".equals(SecurityContextHolder.getContext().getAuthentication().getName()))
                throw new AppException(ErrorCode.FORBIDDEN);
            return store;
        }).when(access).requireApprovedStore(anyString());
        when(vouchers.platform(anyString())).thenReturn(Map.of("result", List.of()));
        actor("owner");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    CreateLiveOfferRequest offer(long quantity) {
        return new CreateLiveOfferRequest(productId, variantId, 299000L, quantity, null, Instant.now().plusSeconds(600));
    }

    InventoryCheckoutController.Input input(String token, String subject) {
        return new InventoryCheckoutController.Input(List.of(new InventoryCheckoutController.Line(productId, variantId, 1, liveId, token)), subject);
    }

    @Test
    void livePricesRequireRoomProofAndKeepCapturedOrdersAfterShopHidesProduct() {
        var state = commerce.createOffer(liveId, offer(2));
        assertEquals(productId, state.pinnedProductId());
        assertEquals(299000L, state.offers().getFirst().priceVnd());
        assertEquals(329000L, variants.findById(variantId).orElseThrow().getPriceVnd());
        assertThrows(AppException.class, () -> inventory.quote(input(null, "buyer"), internal));
        var token = purchases.issue(liveId, jwt("buyer", "USER"));
        assertThrows(AppException.class, () -> inventory.quote(input(token, "another-buyer"), internal));
        assertThrows(AppException.class, () -> purchases.verify(token + "tampered", liveId, "buyer"));
        var reservation = inventory.reserve("captured", input(token, "buyer"), internal).result();
        var line = (Map<?, ?>) ((List<?>) reservation.get("lines")).getFirst();
        assertEquals(299000L, line.get("unitPriceVnd"));
        assertEquals("Shirt", line.get("name"));
        assertEquals("WHITE-M", line.get("sku"));
        assertEquals("https://example.test/shirt.webp", line.get("imageUrl"));
        assertEquals(1L, commerce.get(liveId, true).offers().getFirst().reservedQuantity());
        var product = products.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setName("Changed later");
        products.saveAndFlush(product);
        var retried = inventory.reserve("captured", input("expired-proof-does-not-reprice-existing-order", "buyer"), internal).result();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(json.readTree(json.writeValueAsString(reservation)), json.readTree(json.writeValueAsString(retried)));
        inventory.commit("captured", internal);
        assertEquals(9, variants.findById(variantId).orElseThrow().getStockQuantity());
        assertThrows(Exception.class, () -> inventory.reserve("new-order", input(token, "buyer"), internal));
    }

    @Test
    void quoteDoesNotConsumeOfferAndReleaseReturnsStockAndOfferQuotaOnce() {
        commerce.createOffer(liveId, offer(1));
        String token = purchases.issue(liveId, jwt("buyer", "USER"));
        inventory.quote(input(token, "buyer"), internal);
        assertEquals(0L, commerce.get(liveId, true).offers().getFirst().reservedQuantity());
        inventory.reserve("reserved", input(token, "buyer"), internal);
        assertEquals("SOLD_OUT", commerce.get(liveId, true).offers().getFirst().status());
        inventory.release("reserved", internal);
        inventory.release("reserved", internal);
        assertEquals(10, variants.findById(variantId).orElseThrow().getStockQuantity());
        assertEquals(0L, commerce.get(liveId, true).offers().getFirst().reservedQuantity());
    }

    @Test
    void livePriceIsScopedToOneSessionAndUnaffectedByLaterCatalogPriceChanges() {
        commerce.createOffer(liveId, offer(5));
        var other = lives.saveAndFlush(Livestream.builder().storeId(storeId).title("Other live").status(LivestreamStatus.LIVE).productRefs(Map.of("productIds", List.of(productId))).build());
        String proof = purchases.issue(liveId, jwt("buyer", "USER"));
        var regular = new InventoryCheckoutController.Input(List.of(new InventoryCheckoutController.Line(productId, variantId, 1)), "buyer");
        var elsewhere = new InventoryCheckoutController.Input(List.of(new InventoryCheckoutController.Line(productId, variantId, 1, other.getLivestreamId())), "buyer");
        assertEquals(329000L, unitPrice(inventory.quote(regular, internal).result()));
        assertEquals(329000L, unitPrice(inventory.quote(elsewhere, internal).result()));
        assertEquals(299000L, unitPrice(inventory.reserve("fixed-live-price", input(proof, "buyer"), internal).result()));
        var product = products.findById(productId).orElseThrow();
        product.setPriceVnd(349000L);
        products.saveAndFlush(product);
        var variant = variants.findById(variantId).orElseThrow();
        variant.setPriceVnd(349000L);
        variants.saveAndFlush(variant);
        assertEquals(349000L, unitPrice(inventory.quote(regular, internal).result()));
        assertEquals(349000L, unitPrice(inventory.quote(elsewhere, internal).result()));
        assertEquals(299000L, unitPrice(inventory.quote(input(proof, "buyer"), internal).result()));
        commerce.stopOffer(liveId, commerce.get(liveId, true).offers().getFirst().id());
        assertEquals(349000L, unitPrice(inventory.quote(input(proof, "buyer"), internal).result()));
        assertEquals(299000L, unitPrice(inventory.reserve("fixed-live-price", input(proof, "buyer"), internal).result()));
    }

    long unitPrice(Map<String, Object> result) {
        return ((Number) ((Map<?, ?>) ((List<?>) result.get("lines")).getFirst()).get("unitPriceVnd")).longValue();
    }

    @Test
    void liveOfferAboveTheNewCatalogPriceMustBeRefreshedBeforeNewReservations() {
        commerce.createOffer(liveId, offer(5));
        String proof = purchases.issue(liveId, jwt("buyer", "USER"));
        inventory.reserve("previous-price", input(proof, "buyer"), internal);
        var variant = variants.findById(variantId).orElseThrow();
        variant.setPriceVnd(250000L);
        variants.saveAndFlush(variant);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> inventory.reserve("new-price", input(proof, "buyer"), internal));
        assertEquals(9, variants.findById(variantId).orElseThrow().getStockQuantity());
        assertEquals(299000L, unitPrice(inventory.reserve("previous-price", input(proof, "buyer"), internal).result()));
    }

    @Test
    void storeOwnerPinsWithoutAffiliateAvailabilityAndUnrelatedUsersCannotControlTheLive() {
        var live = lives.findById(liveId).orElseThrow();
        live.setCollaboratorRefs(Map.of("collaboratorIds", List.of("kol")));
        live.setParticipantRefs(Map.of("collaboratorResponses", Map.of("kol", "ACCEPTED")));
        lives.saveAndFlush(live);
        when(access.userId()).thenAnswer(call -> SecurityContextHolder.getContext().getAuthentication().getName());
        when(affiliate.verify(anyString(), anyString(), anyString())).thenThrow(new AppException(ErrorCode.CONFLICT, "Affiliate unavailable"));
        assertEquals(productId, livestreams.pinProduct(liveId, productId).productRefs().get("pinnedProductId"));
        assertNull(livestreams.pinProduct(liveId, null).productRefs().get("pinnedProductId"));
        verifyNoInteractions(affiliate);
        doReturn(Map.of("result", Map.of("approvalStatus", "APPROVED", "userId", "kol-user"))).when(affiliate).verify(anyString(), anyString(), anyString());
        actor("unrelated");
        assertThrows(AppException.class, () -> livestreams.pinProduct(liveId, productId));
        actor("kol-user");
        assertEquals(productId, livestreams.pinProduct(liveId, productId).productRefs().get("pinnedProductId"));
        assertThrows(AppException.class, () -> livestreams.pinProduct(liveId, "not-in-this-live"));
    }

    @Test
    void concurrentReservationsCannotExceedTheDiscountedQuantity() throws Exception {
        commerce.createOffer(liveId, offer(1));
        var token = purchases.issue(liveId, jwt("buyer", "USER"));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return inventory.reserve("race-a", input(token, "buyer"), internal).result();
            });
            var second = executor.submit(() -> {
                start.await();
                return inventory.reserve("race-b", input(token, "buyer"), internal).result();
            });
            start.countDown();
            var values = List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            long discounted = values.stream().map(value -> (Map<?, ?>) ((List<?>) value.get("lines")).getFirst()).filter(line -> ((Number) line.get("unitPriceVnd")).longValue() == 299000L).count();
            assertEquals(1, discounted);
            assertEquals(1L, commerce.get(liveId, true).offers().getFirst().reservedQuantity());
            assertEquals(8, variants.findById(variantId).orElseThrow().getStockQuantity());
        }
    }

    @Test
    void onlyOwnerSetsPricesAndOrdinaryLiveUpdatesCannotForgeOrEraseCommerce() {
        actor("customer");
        assertThrows(AppException.class, () -> commerce.createOffer(liveId, offer(2)));
        actor("owner");
        var state = commerce.createOffer(liveId, offer(2));
        livestreams.update(liveId, new UpdateLivestreamRequest(null, null, null, null, null, Map.of("productIds", List.of(productId), "offers", List.of(Map.of("priceVnd", 1))), Map.of("voucherDrops", List.of(Map.of("code", "FORGED"))), null, null, null, null));
        assertEquals(state.offers(), commerce.get(liveId, true).offers());
        assertTrue(commerce.get(liveId, true).voucherDrops().isEmpty());
        assertThrows(AppException.class, () -> commerce.createOffer(liveId, new CreateLiveOfferRequest(productId, variantId, 500000L, 1L, null, null)));
    }

    @Test
    void voucherSchedulesAreServerTimedAndDoNotExposeUnreleasedCodes() {
        var voucher = new LinkedHashMap<String, Object>();
        voucher.putAll(Map.of("voucherId", "voucher", "code", "LIVE30", "issuerType", "STORE", "storeId", storeId, "scopeType", "LIVESTREAM", "livestreamId", liveId, "discountType", "FIXED", "value", 30000, "validFrom", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).toString(), "validUntil", LocalDateTime.now(ZoneOffset.UTC).plusHours(1).toString()));
        when(vouchers.get(anyString(), eq("voucher"))).thenReturn(Map.of("result", voucher));
        var state = commerce.createVoucherDrop(liveId, new CreateLiveVoucherDropRequest("voucher", 10L, Instant.now().plusSeconds(30), Instant.now().plusSeconds(300)));
        assertEquals("SCHEDULED", state.voucherDrops().getFirst().status());
        assertTrue(commerce.get(liveId, false).voucherDrops().isEmpty());
        assertFalse(livestreams.getById(liveId).participantRefs().containsKey("voucherDrops"));
        var live = lives.findById(liveId).orElseThrow();
        var refs = new LinkedHashMap<>(live.getParticipantRefs());
        var row = new LinkedHashMap<>((Map<String, Object>) ((List<?>) refs.get("voucherDrops")).getFirst());
        row.put("releaseAt", Instant.now().minusSeconds(1).toString());
        refs.put("voucherDrops", List.of(row));
        live.setParticipantRefs(refs);
        lives.saveAndFlush(live);
        assertEquals("PUBLISHED", commerce.get(liveId, false).voucherDrops().getFirst().status());
        commerce.cancelVoucherDrop(liveId, state.voucherDrops().getFirst().id());
        assertTrue(commerce.get(liveId, false).voucherDrops().isEmpty());
        actor("customer");
        assertThrows(AppException.class, () -> commerce.createVoucherDrop(liveId, new CreateLiveVoucherDropRequest("voucher", 1L, null, Instant.now().plusSeconds(100))));
    }
}
