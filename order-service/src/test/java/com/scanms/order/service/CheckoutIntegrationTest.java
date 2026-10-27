package com.scanms.order.service;

import com.scanms.order.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:checkout-orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret=test-only-secret", "scanms.finance.dispatch-delay-ms=3600000"})
@org.springframework.test.context.ActiveProfiles("test")
class CheckoutIntegrationTest {
    @Autowired
    javax.sql.DataSource migrationDataSource;

    @Test
    void postgresMigrationsCanBeAppliedTwice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SCANMS_TEST_POSTGRES_PREFIX") != null);
        try (var connection = migrationDataSource.getConnection(); var statement = connection.createStatement()) {
            for (int pass = 0; pass < 2; pass++) {
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db", "finance-checkout-upgrade.sql")));
            }
        }
    }

    // Optional real PostgreSQL run; only explicitly named QA databases may be dropped by Hibernate.
    @org.springframework.test.context.DynamicPropertySource
    static void postgresDatabase(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix = System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if (prefix == null || prefix.isBlank()) return;
        if (!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_"))
            throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        r.add("spring.datasource.url", () -> prefix + "orders");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER", "scanms_qa"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD", ""));
    }

    @Autowired
    CheckoutService checkout;
    @Autowired
    FinanceDispatch financeDispatch;
    @Autowired
    OrderRepository orders;
    @Autowired
    SellerOrderRepository sellers;
    @Autowired
    OrderItemRepository items;
    @Autowired
    com.scanms.order.mapper.OrderItemMapper itemMapper;
    @Autowired
    ReturnRequestRepository returns;
    @Autowired
    DiscountAllocationRepository allocations;
    @Autowired
    CheckoutShippingPolicy shippingPolicy;
    @Autowired
    org.springframework.web.context.WebApplicationContext webContext;
    @MockitoBean
    JwtDecoder decoder;
    @MockitoBean
    GhnSandboxShipping ghn;
    static final AtomicInteger reserves = new AtomicInteger();
    static final AtomicBoolean failNext = new AtomicBoolean();
    static final AtomicBoolean multiStore = new AtomicBoolean();
    static final AtomicInteger voucherCommits = new AtomicInteger();
    static final AtomicInteger productCancels = new AtomicInteger(), voucherCancels = new AtomicInteger();
    static final HttpServer server = server();
    final Jwt jwt = Jwt.withTokenValue("customer-token").header("alg", "RS256").subject("identity").build();

    static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                byte[] body = "{\"access_token\":\"order-service-token\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/user/api/v1/users", exchange -> {
                byte[] body = "{\"result\":{\"userId\":\"customer\",\"identitySubject\":\"identity\",\"status\":\"ACTIVE\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/promotion/api/v1/referrals/attribution/", exchange -> {
                String ref = exchange.getRequestURI().getPath().substring(exchange.getRequestURI().getPath().lastIndexOf('/') + 1);
                String product = ref.equals("ref-b") || ref.equals("wrong-product") ? "product-b" : "product";
                String owner = ref.equals("self") ? "customer" : "creator-user";
                byte[] body = ("{\"result\":{\"referralLinkId\":\"" + ref + "\",\"productId\":\"" + product + "\",\"collaboratorId\":\"kol\",\"userId\":\"" + owner + "\"}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(ref.equals("expired") ? 410 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/product/api/v1/inventory/reservations", exchange -> {
                if (exchange.getRequestURI().getPath().endsWith("/cancel")) productCancels.incrementAndGet();
                boolean preview = exchange.getRequestURI().getPath().endsWith("/quote");
                if (!preview) reserves.incrementAndGet();
                boolean fail = !preview && failNext.getAndSet(false);
                int status = fail ? 503 : "Bearer order-service-token".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? 200 : 403;
                String second = multiStore.get() ? ",{\"productId\":\"product-b\",\"variantId\":\"variant-b\",\"storeId\":\"store-b\",\"storeName\":\"Shop B\",\"name\":\"Product B\",\"quantity\":1,\"unitPriceVnd\":25000,\"grossAmountVnd\":25000}" : "";
                byte[] body = (status == 200 ? "{\"result\":{\"lines\":[{\"productId\":\"product\",\"variantId\":\"variant\",\"storeId\":\"store\",\"storeName\":\"Shop\",\"name\":\"Product\",\"quantity\":2,\"unitPriceVnd\":15000,\"grossAmountVnd\":30000}" + second + "]}}" : "{\"message\":\"Source unavailable\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });


            server.createContext("/promotion/api/v1/checkout-vouchers", exchange -> {
                assertEquals("Bearer order-service-token", exchange.getRequestHeaders().getFirst("Authorization"));
                if (exchange.getRequestURI().getPath().endsWith("/commit")) voucherCommits.incrementAndGet();
                else if (exchange.getRequestURI().getPath().endsWith("/cancel")) voucherCancels.incrementAndGet();
                else assertEquals("30000", exchange.getRequestHeaders().getFirst("X-Shipping-Fee-Per-Store-Vnd"));
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (request.contains("INVALID")) {
                    byte[] error = "{\"message\":\"Voucher unavailable\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(409, error.length);
                    exchange.getResponseBody().write(error);
                    exchange.close();
                    return;
                }
                boolean pair = request.contains("shippingCode") && request.contains("GOODS");
                String goods = pair ? ",{\"variantId\":\"variant\",\"voucherId\":\"goods-voucher\",\"allocatedAmountVnd\":10000,\"fundedBy\":\"PLATFORM\"}" : "";
                String body = "{\"result\":{\"discountAmountVnd\":" + (pair ? 40000 : 30000) + ",\"allocations\":[{\"benefitType\":\"SHIPPING\",\"storeId\":\"store\",\"voucherId\":\"ship-voucher\",\"allocatedAmountVnd\":30000,\"fundedBy\":\"PLATFORM\"}" + goods + "]}}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/promotion/api/v1/referrals/clicks/", exchange -> {
                assertEquals("Bearer order-service-token", exchange.getRequestHeaders().getFirst("Authorization"));
                assertTrue(exchange.getRequestURI().getQuery().contains("productId=product"));
                assertTrue(exchange.getRequestURI().getQuery().contains("customerId=customer"));
                boolean expired = exchange.getRequestURI().getPath().contains("expired"), invalid = exchange.getRequestURI().getPath().contains("invalid");
                String result = expired ? "{}" : "{\"referralLinkId\":\"ref\",\"referralClickId\":\"click\",\"sourceVideoId\":\"video\",\"collaboratorId\":\"kol\"}";
                String path = exchange.getRequestURI().getPath();
                if (path.contains("click-a") || path.contains("click-b")) {
                    String suffix = path.contains("click-a") ? "a" : "b";
                    result = "{\"referralLinkId\":\"ref-" + suffix + "\",\"referralClickId\":\"click-" + suffix + "\",\"collaboratorId\":\"kol\"}";
                }
                byte[] body = (invalid ? "{\"message\":\"Wrong product\"}" : "{\"result\":" + result + "}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(invalid ? 409 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void sources(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        r.add("clients.user.url", () -> base + "/user");
        r.add("clients.product.url", () -> base + "/product");
        r.add("clients.promotion.url", () -> base + "/promotion");
        r.add("scanms.finance.token-url", () -> base + "/token");
    }

    @AfterAll
    static void close() {
        server.stop(0);
    }

    @BeforeEach
    void setup() {
        returns.deleteAll();
        allocations.deleteAll();
        items.deleteAll();
        sellers.deleteAll();
        orders.deleteAll();
        reserves.set(0);
        failNext.set(false);
        multiStore.set(false);
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 0L);
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "simulation", false);
    }

    CheckoutService.Input input(String key, int quantity) {
        return new CheckoutService.Input(key, "Customer", "0900000000", "Delivery address", "PAYOS", true, null, null, List.of(new CheckoutService.Line("product", "variant", quantity)));
    }

    @Test
    void freeshipQuoteAndPersistedOrderChargeShippingOnlyAndKeepProductRefundBasis() {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 30000L);
        var quote = checkout.quote(new CheckoutService.QuoteInput(List.of(new CheckoutService.Line("product", "variant", 2)), "SHIP"), jwt);
        assertEquals(0L, quote.get("discountAmount"));
        assertEquals(30000L, quote.get("shippingDiscountVnd"));
        assertEquals(30000L, quote.get("shippingFeeVnd"));
        assertEquals(30000L, quote.get("finalAmount"));
        var input = new CheckoutService.Input("freeship-test", "Customer", "0900000000", "Delivery address", "WALLET", true, "SHIP", null,
                List.of(new CheckoutService.Line("product", "variant", 2)), 30000L);
        var result = checkout.checkout(input, jwt);
        assertEquals(30000L, result.get("finalAmount"));
        assertEquals(0L, result.get("shippingFeeVnd"));
        assertEquals(30000L, result.get("shippingOriginalFeeVnd"));
        assertEquals(30000L, result.get("shippingDiscountVnd"));
        assertEquals(30000L, items.findAll().getFirst().getNetPaidAmountVnd());
        assertEquals(0L, items.findAll().getFirst().getDiscountAmountVnd());
        assertEquals(0, allocations.count());
        assertEquals(result.get("orderId"), checkout.checkout(input, jwt).get("orderId"));
        assertEquals(1, items.count());
    }

    @Test
    void paidOrderCancellationIsDispatchedEvenAfterResourcesWereCommitted() {
        productCancels.set(0);
        voucherCancels.set(0);
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 30000L);
        var input = new CheckoutService.Input("paid-cancel-dispatch", "Customer", "0900000000", "Delivery address", "WALLET", true, "SHIP", null, List.of(new CheckoutService.Line("product", "variant", 2)), 30000L);
        String id = checkout.checkout(input, jwt).get("orderId").toString();
        var order = orders.findById(id).orElseThrow();
        order.setStatus(com.scanms.order.constant.OrderStatus.PAID);
        orders.saveAndFlush(order);
        financeDispatch.dispatch();
        assertEquals("commit", orders.findById(id).orElseThrow().getSagaState().get("resourceOutcome"));
        order = orders.findById(id).orElseThrow();
        order.setStatus(com.scanms.order.constant.OrderStatus.CANCELLED);
        orders.saveAndFlush(order);
        for (var seller : sellers.findByOrderId(id)) {
            seller.setStatus(com.scanms.order.constant.SellerOrderStatus.CANCELLED);
            sellers.saveAndFlush(seller);
        }
        financeDispatch.dispatch();
        financeDispatch.dispatch();
        assertEquals(1, productCancels.get());
        assertEquals(1, voucherCancels.get());
        assertEquals("cancel", orders.findById(id).orElseThrow().getSagaState().get("resourceOutcome"));
    }

    @Test
    void verifiedReferralIsPersistedPerProductAndRetryNeverReattributesAnOrder() {
        multiStore.set(true);
        var input = new CheckoutService.Input("referrals", "Customer", "0900000000", "Delivery address", "PAYOS", true, null, null, List.of(
                new CheckoutService.Line("product", "variant", 2, null, "ref-a", "click-a"), new CheckoutService.Line("product-b", "variant-b", 1, null, "ref-b", "click-b")));
        var first = checkout.checkout(input, jwt);
        checkout.checkout(input, jwt);
        assertEquals(2, items.count());
        assertEquals(1, reserves.get());
        for (var item : items.findAll())
            assertEquals(item.getProductId().equals("product") ? "ref-a" : "ref-b", item.getReferralLinkId());
        assertNotNull(first.get("orderId"));
    }

    @Test
    void bareReferralIdsNeverCreateAttributedItemsWithoutVerifiedClick() {
        for (String ref : List.of("wrong-product", "self", "expired")) {
            var input = new CheckoutService.Input(ref, "Customer", "0900000000", "Delivery address", "PAYOS", true, null, null,
                    List.of(new CheckoutService.Line("product", "variant", 2, null, ref)));
            assertNotNull(checkout.checkout(input, jwt).get("orderId"));
        }
        assertEquals(3, items.count());
        assertEquals(3, reserves.get());
        assertTrue(items.findAll().stream().allMatch(item -> item.getReferralLinkId() == null && item.getRuleSnapshot() == null));
    }

    @Test
    void checkoutUsesServerPriceAndCreatesSellerItemsOnlyOnce() {
        var first = checkout.checkout(input("key", 2), jwt);
        var second = checkout.checkout(input("key", 2), jwt);
        assertEquals(first.get("orderId"), second.get("orderId"));
        assertEquals(30000L, first.get("finalAmount"));
        assertEquals(1, reserves.get());
        assertEquals(1, orders.count());
        assertEquals(1, sellers.count());
        assertEquals(1, items.count());
        assertEquals(30000L, items.findAll().getFirst().getNetPaidAmountVnd());
        assertThrows(ResponseStatusException.class, () -> checkout.checkout(input("key", 1), jwt));
        assertEquals(1, reserves.get());
    }

    @Test
    void sandboxAddressQuoteMatchesCapturedOrderAndStaleFeeCannotReserve() {
        whenSandbox();
        var a = new CheckoutService.ShippingDestination("Province", "District", "Ward A", "123 Test street");
        var b = new CheckoutService.ShippingDestination("Province", "District", "Ward B", "123 Test street");
        var lines = List.of(new CheckoutService.Line("product", "variant", 1));
        assertEquals(25000L, checkout.quote(new CheckoutService.QuoteInput(lines, null, null, a), jwt).get("shippingFeeVnd"));
        assertEquals(35000L, checkout.quote(new CheckoutService.QuoteInput(lines, null, null, b), jwt).get("shippingFeeVnd"));
        assertThrows(ResponseStatusException.class, () -> checkout.checkout(new CheckoutService.Input("stale-ghn", "Customer", "0901234567", "123 Test street, Ward B, District, Province", "WALLET", true, null, null, lines, 25000L, null, b), jwt));
        assertEquals(0, orders.count());
        assertEquals(0, reserves.get());
        var command = new CheckoutService.Input("valid-ghn", "Customer", "0901234567", "123 Test street, Ward B, District, Province", "WALLET", true, null, null, lines, 35000L, null, b);
        var result = checkout.checkout(command, jwt);
        assertEquals(65000L, result.get("finalAmount"));
        var seller = sellers.findAll().getFirst();
        assertEquals(35000L, ((Number) seller.getTotalsSnapshot().get("shippingFeeVnd")).longValue());
        assertEquals("GHN_SANDBOX", seller.getTotalsSnapshot().get("shippingMode"));
        assertEquals(30000L, items.findAll().getFirst().getNetPaidAmountVnd());
        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(ghn).quote(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
        assertEquals(result.get("orderId"), checkout.checkout(command, jwt).get("orderId"));
        assertEquals(65000L, checkout.checkout(command, jwt).get("finalAmount"));
        assertEquals(1, reserves.get());
    }

    void whenSandbox() {
        org.mockito.Mockito.when(ghn.enabled()).thenReturn(true);
        org.mockito.Mockito.when(ghn.mode()).thenReturn(Map.of("shippingMode", "GHN_SANDBOX", "shippingPayer", "CUSTOMER", "shippingSettlementStatus", "SANDBOX"));
        org.mockito.Mockito.when(ghn.quote(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> {
            CheckoutService.ShippingDestination d = call.getArgument(1);
            long fee = d.ward().equals("Ward A") ? 25000L : 35000L;
            return new GhnSandboxShipping.ParcelQuote(fee, Map.of("shippingMode", "GHN_SANDBOX", "shippingPayer", "CUSTOMER", "shippingOriginalFeeVnd", fee));
        });
    }

    @Test
    void checkoutRecordsAnAbsolutePlacementTimeForCommissionDeals() {
        var before = java.time.Instant.now().minusSeconds(1);
        checkout.checkout(input("deal-timestamp", 1), jwt);
        var item = items.findAll().getFirst();
        assertNotNull(item.getPlacedAt());
        assertFalse(item.getPlacedAt().isBefore(before));
        assertEquals(item.getPlacedAt(), itemMapper.toResponse(item).placedAt());
        var timestamp = item.getPlacedAt();
        checkout.checkout(input("deal-timestamp", 1), jwt);
        assertEquals(timestamp, items.findById(item.getOrderItemId()).orElseThrow().getPlacedAt());
    }


    @Test
    void pairDiscountsGoodsAndShippingSeparatelyAndDispatchesShippingOnlyRedemption() {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 30000L);
        var lines = List.of(new CheckoutService.Line("product", "variant", 2));
        var quote = checkout.quote(new CheckoutService.QuoteInput(lines, "GOODS", "SHIP"), jwt);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> checkout.quote(new CheckoutService.QuoteInput(lines, "INVALID", "SHIP"), jwt)).getStatusCode().value());
        assertEquals(10000L, quote.get("discountAmount"));
        assertEquals(30000L, quote.get("shippingDiscountVnd"));
        assertEquals(20000L, quote.get("finalAmount"));
        assertEquals("MIXED", quote.get("benefitType"));
        var input = new CheckoutService.Input("pair", "Customer", "0900000000", "Delivery address", "WALLET", true, "GOODS", null, lines, 30000L, "SHIP");
        var created = checkout.checkout(input, jwt);
        assertEquals(20000L, created.get("finalAmount"));
        assertEquals(0L, created.get("shippingFeeVnd"));
        assertEquals(10000L, items.findAll().getFirst().getDiscountAmountVnd());
        assertEquals(20000L, items.findAll().getFirst().getNetPaidAmountVnd());
        assertEquals(1, allocations.count());
        assertEquals(created.get("orderId"), checkout.checkout(input, jwt).get("orderId"));
        assertEquals(1, items.count());
        var shippingOnly = new CheckoutService.Input("shipping-dispatch", "Customer", "0900000000", "Delivery address", "WALLET", true, null, null, lines, 30000L, "SHIP");
        var ship = checkout.checkout(shippingOnly, jwt);
        var order = orders.findById(ship.get("orderId").toString()).orElseThrow();
        order.setStatus(com.scanms.order.constant.OrderStatus.PAID);
        orders.saveAndFlush(order);
        int before = voucherCommits.get();
        financeDispatch.dispatch();
        assertEquals(before + 1, voucherCommits.get());
        assertEquals(true, orders.findById(order.getOrderId()).orElseThrow().getSagaState().get("resourcesFinalized"));
    }


    @Test
    void failedSourceRetainsOneCheckoutReferenceForSafeRetry() {
        failNext.set(true);
        var failure = assertThrows(ResponseStatusException.class, () -> checkout.checkout(input("retry", 2), jwt));
        assertEquals(503, failure.getStatusCode().value());
        assertEquals(1, orders.count());
        assertEquals(0, items.count());
        String id = orders.findAll().getFirst().getOrderId();
        assertEquals(id, checkout.checkout(input("retry", 2), jwt).get("orderId"));
        assertEquals(1, items.count());
        assertEquals(1, orders.count());
    }

    @Test
    void changedPayableCannotCreateReadyOrderAtAnUnexpectedPrice() {
        var request = new CheckoutService.Input("expected-total", "Customer", "0900000000", "Delivery address", "WALLET", true, null, null, input("unused", 2).items(), null, null, 100L, null);
        var error = assertThrows(ResponseStatusException.class, () -> checkout.checkout(request, jwt));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(0, items.count());
        assertTrue(orders.findAll().stream().noneMatch(order -> "READY".equals(order.getSagaState().get("checkoutState"))));
    }

    @Test
    void paidOrderWithoutVoucherFinalizesInventoryWithoutVoucherReservation() {
        var created = checkout.checkout(input("no-voucher", 2), jwt);
        String orderId = created.get("orderId").toString();
        var order = orders.findById(orderId).orElseThrow();
        order.setStatus(com.scanms.order.constant.OrderStatus.PAID);
        orders.saveAndFlush(order);

        financeDispatch.dispatch();

        assertEquals(true, orders.findById(orderId).orElseThrow().getSagaState().get("resourcesFinalized"));
    }

    @Test
    void shippingIsCapturedSeparatelyAndIdempotentRetryKeepsItsOriginalAmount() {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 30000L);
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "simulation", true);
        var quote = checkout.quote(new CheckoutService.QuoteInput(input("shipping", 2).items(), null), jwt);
        assertEquals(30000L, quote.get("shippingFeeVnd"));
        assertEquals(60000L, quote.get("finalAmount"));
        var input = new CheckoutService.Input("shipping", "Customer", "0900000000", "Delivery address", "WALLET", true, null, null, input("shipping", 2).items(), 30000L);
        var created = checkout.checkout(input, jwt);
        assertEquals(60000L, created.get("finalAmount"));
        var seller = sellers.findAll().getFirst();
        assertEquals(30000L, ((Number) seller.getTotalsSnapshot().get("netPaidVnd")).longValue());
        assertEquals(30000L, ((Number) seller.getTotalsSnapshot().get("shippingFeeVnd")).longValue());
        assertEquals("CUSTOMER", seller.getTotalsSnapshot().get("shippingPayer"));
        assertEquals("SIMULATED", seller.getTotalsSnapshot().get("shippingSettlementStatus"));
        assertEquals(30000L, items.findAll().getFirst().getNetPaidAmountVnd());
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 50000L);
        assertEquals(60000L, checkout.checkout(input, jwt).get("finalAmount"));
        assertEquals(1, orders.count());
        assertEquals(1, items.count());
    }

    @Test
    void multiStoreChargesPerSellerAndStaleQuoteCreatesNoOrderOrInventoryHold() {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 30000L);
        multiStore.set(true);
        var lines = List.of(new CheckoutService.Line("product", "variant", 2), new CheckoutService.Line("product-b", "variant-b", 1));
        var quote = checkout.quote(new CheckoutService.QuoteInput(lines, null), jwt);
        assertEquals(60000L, quote.get("shippingFeeVnd"));
        assertEquals(115000L, quote.get("finalAmount"));
        var stale = new CheckoutService.Input("stale", "Customer", "0900000000", "Delivery address", "WALLET", true, null, null, lines, 30000L);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> checkout.checkout(stale, jwt)).getStatusCode().value());
        assertEquals(0, orders.count());
        var correct = new CheckoutService.Input("multi", "Customer", "0900000000", "Delivery address", "WALLET", true, null, null, lines, 60000L);
        var order = checkout.checkout(correct, jwt);
        assertEquals(115000L, order.get("finalAmount"));
        assertEquals(2, sellers.count());
        assertEquals(115000L, sellers.findAll().stream().mapToLong(s -> ((Number) s.getTotalsSnapshot().get("customerTotalVnd")).longValue()).sum());
        assertEquals(55000L, items.findAll().stream().mapToLong(i -> i.getNetPaidAmountVnd()).sum());
    }

    @Test
    void paymentAcknowledgementMustCoverShippingAsWellAsGoods() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy, "fee", 30000L);
        var created = checkout.checkout(input("payment-shipping", 2), jwt);
        String id = created.get("orderId").toString();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var principal = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.claim("realm_access", Map.of("roles", List.of("PAYMENT_INTERNAL"))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/finance/orders/" + id + "/paid").with(principal)
                        .contentType("application/json").content("{\"paymentId\":\"payment\",\"amountVnd\":30000,\"currency\":\"VND\",\"verifiedAt\":\"2026-10-22T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/finance/orders/" + id + "/paid").with(principal)
                        .contentType("application/json").content("{\"paymentId\":\"payment\",\"amountVnd\":60000,\"currency\":\"VND\",\"verifiedAt\":\"2026-10-22T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(com.scanms.order.constant.OrderStatus.PAID, orders.findById(id).orElseThrow().getStatus());
        assertEquals(30000L, ((Number) orders.findById(id).orElseThrow().getSagaState().get("shippingFeeVnd")).longValue());
    }

    @Test
    void verifiedVideoClickIsPersistedOnceAndExpiredClicksDoNotBlockPurchase() {
        var input = new CheckoutService.Input("video-attribution", "Customer", "0900000000", "Delivery address", "PAYOS", true, null, null,
                List.of(new CheckoutService.Line("product", "variant", 2, null, "client-forged-ref", "click")));
        var order = checkout.checkout(input, jwt);
        var item = items.findAll().getFirst();
        assertEquals("ref", item.getReferralLinkId());
        assertEquals("video", item.getRuleSnapshot().get("sourceVideoId"));
        assertEquals(30000, item.getNetPaidAmountVnd());
        assertEquals(order.get("orderId"), checkout.checkout(input, jwt).get("orderId"));
        assertEquals(1, items.count());
        checkout.checkout(new CheckoutService.Input("expired-click", "Customer", "0900000000", "Delivery address", "PAYOS", true, null, null,
                List.of(new CheckoutService.Line("product", "variant", 2, null, "ref", "expired"))), jwt);
        assertEquals(1, items.findAll().stream().filter(i -> i.getReferralLinkId() != null).count());
    }


    @Test
    void invalidAttributionCannotReserveStockOrCreateAttributedItems() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> checkout.checkout(new CheckoutService.Input("invalid-click", "Customer", "0900000000", "Delivery address", "PAYOS", true, null, null,
                List.of(new CheckoutService.Line("product", "variant", 2, null, "ref", "invalid"))), jwt)).getStatusCode().value());
        assertEquals(0, items.count());
        assertEquals(0, reserves.get());
    }


}