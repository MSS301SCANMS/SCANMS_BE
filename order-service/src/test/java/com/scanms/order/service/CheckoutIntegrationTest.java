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
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:checkout-orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN","scanms.finance.client-secret=test-only-secret","scanms.finance.dispatch-delay-ms=3600000"})
@org.springframework.test.context.ActiveProfiles("test")
class CheckoutIntegrationTest {
    @Autowired javax.sql.DataSource migrationDataSource;
    @Test void postgresMigrationsCanBeAppliedTwice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SCANMS_TEST_POSTGRES_PREFIX")!=null);
        try(var connection=migrationDataSource.getConnection();var statement=connection.createStatement()) {
            for(int pass=0;pass<2;pass++) {
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","finance-checkout-upgrade.sql")));
            }
        }
    }

    // Optional real PostgreSQL run; only explicitly named QA databases may be dropped by Hibernate.
    @org.springframework.test.context.DynamicPropertySource
    static void postgresDatabase(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix=System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if(prefix==null || prefix.isBlank()) return;
        if(!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_")) throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        r.add("spring.datasource.url",() -> prefix+"orders");
        r.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        r.add("spring.datasource.username",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER","scanms_qa"));
        r.add("spring.datasource.password",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD",""));
    }

    @Autowired CheckoutService checkout;
    @Autowired FinanceDispatch financeDispatch;
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderItemRepository items;
    @Autowired ReturnRequestRepository returns;
    @Autowired DiscountAllocationRepository allocations;
    @Autowired CheckoutShippingPolicy shippingPolicy;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @MockitoBean JwtDecoder decoder;
    static final AtomicInteger reserves=new AtomicInteger(); static final AtomicBoolean failNext=new AtomicBoolean();
    static final AtomicBoolean multiStore=new AtomicBoolean();
    static final HttpServer server=server();
    final Jwt jwt=Jwt.withTokenValue("customer-token").header("alg","RS256").subject("identity").build();
    static HttpServer server() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/token",exchange -> { byte[] body="{\"access_token\":\"order-service-token\"}".getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            server.createContext("/user/api/v1/users",exchange -> { byte[] body="{\"result\":{\"userId\":\"customer\",\"identitySubject\":\"identity\",\"status\":\"ACTIVE\"}}".getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            server.createContext("/product/api/v1/inventory/reservations",exchange -> {
                reserves.incrementAndGet(); boolean fail=failNext.getAndSet(false);
                int status=fail?503:"Bearer order-service-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))?200:403;
                String second=multiStore.get()?",{\"productId\":\"product-b\",\"variantId\":\"variant-b\",\"storeId\":\"store-b\",\"storeName\":\"Shop B\",\"name\":\"Product B\",\"quantity\":1,\"unitPriceVnd\":25000,\"grossAmountVnd\":25000}":"";
                byte[] body=(status==200?"{\"result\":{\"lines\":[{\"productId\":\"product\",\"variantId\":\"variant\",\"storeId\":\"store\",\"storeName\":\"Shop\",\"name\":\"Product\",\"quantity\":2,\"unitPriceVnd\":15000,\"grossAmountVnd\":30000}"+second+"]}}":"{\"message\":\"Source unavailable\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(status,body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start(); return server;
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void sources(DynamicPropertyRegistry r) {
        String base="http://127.0.0.1:"+server.getAddress().getPort(); r.add("clients.user.url",() -> base+"/user"); r.add("clients.product.url",() -> base+"/product"); r.add("scanms.finance.token-url",() -> base+"/token");
    }
    @AfterAll static void close() { server.stop(0); }
    @BeforeEach void setup() { returns.deleteAll(); allocations.deleteAll(); items.deleteAll(); sellers.deleteAll(); orders.deleteAll(); reserves.set(0); failNext.set(false); multiStore.set(false); org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"fee",0L); org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"simulation",false); }
    CheckoutService.Input input(String key,int quantity) { return new CheckoutService.Input(key,"Customer","0900000000","Delivery address","PAYOS",true,null,null,List.of(new CheckoutService.Line("product","variant",quantity))); }
    @Test void checkoutUsesServerPriceAndCreatesSellerItemsOnlyOnce() {
        var first=checkout.checkout(input("key",2),jwt); var second=checkout.checkout(input("key",2),jwt);
        assertEquals(first.get("orderId"),second.get("orderId")); assertEquals(30000L,first.get("finalAmount")); assertEquals(1,reserves.get());
        assertEquals(1,orders.count()); assertEquals(1,sellers.count()); assertEquals(1,items.count()); assertEquals(30000L,items.findAll().getFirst().getNetPaidAmountVnd());
        assertThrows(ResponseStatusException.class,() -> checkout.checkout(input("key",1),jwt)); assertEquals(1,reserves.get());
    }
    @Test void failedSourceRetainsOneCheckoutReferenceForSafeRetry() {
        failNext.set(true); assertThrows(RestClientResponseException.class,() -> checkout.checkout(input("retry",2),jwt));
        assertEquals(1,orders.count()); assertEquals(0,items.count()); String id=orders.findAll().getFirst().getOrderId();
        assertEquals(id,checkout.checkout(input("retry",2),jwt).get("orderId")); assertEquals(1,items.count()); assertEquals(1,orders.count());
    }
    @Test void paidOrderWithoutVoucherFinalizesInventoryWithoutVoucherReservation() {
        var created=checkout.checkout(input("no-voucher",2),jwt);
        String orderId=created.get("orderId").toString();
        var order=orders.findById(orderId).orElseThrow();
        order.setStatus(com.scanms.order.constant.OrderStatus.PAID);
        orders.saveAndFlush(order);

        financeDispatch.dispatch();

        assertEquals(true,orders.findById(orderId).orElseThrow().getSagaState().get("resourcesFinalized"));
    }
    @Test void shippingIsCapturedSeparatelyAndIdempotentRetryKeepsItsOriginalAmount() {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"fee",30000L);
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"simulation",true);
        var quote=checkout.quote(new CheckoutService.QuoteInput(input("shipping",2).items(),null),jwt);
        assertEquals(30000L,quote.get("shippingFeeVnd"));assertEquals(60000L,quote.get("finalAmount"));
        var input=new CheckoutService.Input("shipping","Customer","0900000000","Delivery address","WALLET",true,null,null,input("shipping",2).items(),30000L);
        var created=checkout.checkout(input,jwt);assertEquals(60000L,created.get("finalAmount"));
        var seller=sellers.findAll().getFirst();assertEquals(30000L,((Number)seller.getTotalsSnapshot().get("netPaidVnd")).longValue());
        assertEquals(30000L,((Number)seller.getTotalsSnapshot().get("shippingFeeVnd")).longValue());
        assertEquals("CUSTOMER",seller.getTotalsSnapshot().get("shippingPayer"));assertEquals("SIMULATED",seller.getTotalsSnapshot().get("shippingSettlementStatus"));
        assertEquals(30000L,items.findAll().getFirst().getNetPaidAmountVnd());
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"fee",50000L);
        assertEquals(60000L,checkout.checkout(input,jwt).get("finalAmount"));assertEquals(1,orders.count());assertEquals(1,items.count());
    }
    @Test void multiStoreChargesPerSellerAndStaleQuoteCreatesNoOrderOrInventoryHold() {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"fee",30000L);multiStore.set(true);
        var lines=List.of(new CheckoutService.Line("product","variant",2),new CheckoutService.Line("product-b","variant-b",1));
        var quote=checkout.quote(new CheckoutService.QuoteInput(lines,null),jwt);assertEquals(60000L,quote.get("shippingFeeVnd"));assertEquals(115000L,quote.get("finalAmount"));
        var stale=new CheckoutService.Input("stale","Customer","0900000000","Delivery address","WALLET",true,null,null,lines,30000L);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->checkout.checkout(stale,jwt)).getStatusCode().value());assertEquals(0,orders.count());
        var correct=new CheckoutService.Input("multi","Customer","0900000000","Delivery address","WALLET",true,null,null,lines,60000L);
        var order=checkout.checkout(correct,jwt);assertEquals(115000L,order.get("finalAmount"));assertEquals(2,sellers.count());
        assertEquals(115000L,sellers.findAll().stream().mapToLong(s->((Number)s.getTotalsSnapshot().get("customerTotalVnd")).longValue()).sum());
        assertEquals(55000L,items.findAll().stream().mapToLong(i->i.getNetPaidAmountVnd()).sum());
    }
    @Test void paymentAcknowledgementMustCoverShippingAsWellAsGoods() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(shippingPolicy,"fee",30000L);
        var created=checkout.checkout(input("payment-shipping",2),jwt);
        String id=created.get("orderId").toString();
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var principal=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token->token.claim("realm_access",Map.of("roles",List.of("PAYMENT_INTERNAL"))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/finance/orders/"+id+"/paid").with(principal)
                .contentType("application/json").content("{\"paymentId\":\"payment\",\"amountVnd\":30000,\"currency\":\"VND\",\"verifiedAt\":\"2026-10-22T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/finance/orders/"+id+"/paid").with(principal)
                .contentType("application/json").content("{\"paymentId\":\"payment\",\"amountVnd\":60000,\"currency\":\"VND\",\"verifiedAt\":\"2026-10-22T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(com.scanms.order.constant.OrderStatus.PAID,orders.findById(id).orElseThrow().getStatus());
        assertEquals(30000L,((Number)orders.findById(id).orElseThrow().getSagaState().get("shippingFeeVnd")).longValue());
    }
}
