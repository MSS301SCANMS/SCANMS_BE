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
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderItemRepository items;
    @Autowired ReturnRequestRepository returns;
    @Autowired DiscountAllocationRepository allocations;
    @MockitoBean JwtDecoder decoder;
    static final AtomicInteger reserves=new AtomicInteger(); static final AtomicBoolean failNext=new AtomicBoolean();
    static final HttpServer server=server();
    final Jwt jwt=Jwt.withTokenValue("customer-token").header("alg","RS256").subject("identity").build();
    static HttpServer server() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/token",exchange -> { byte[] body="{\"access_token\":\"order-service-token\"}".getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            server.createContext("/user/api/v1/users/me",exchange -> { byte[] body="{\"result\":{\"userId\":\"customer\",\"identitySubject\":\"identity\",\"status\":\"ACTIVE\"}}".getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            server.createContext("/product/api/v1/inventory/reservations",exchange -> {
                reserves.incrementAndGet(); boolean fail=failNext.getAndSet(false);
                int status=fail?503:"Bearer order-service-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))?200:403;
                byte[] body=(status==200?"{\"result\":{\"lines\":[{\"productId\":\"product\",\"variantId\":\"variant\",\"storeId\":\"store\",\"storeName\":\"Shop\",\"name\":\"Product\",\"quantity\":2,\"unitPriceVnd\":15000,\"grossAmountVnd\":30000}]}}":"{\"message\":\"Source unavailable\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(status,body.length); exchange.getResponseBody().write(body); exchange.close();
            }); server.start(); return server;
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void sources(DynamicPropertyRegistry r) {
        String base="http://127.0.0.1:"+server.getAddress().getPort(); r.add("clients.user.url",() -> base+"/user"); r.add("clients.product.url",() -> base+"/product"); r.add("scanms.finance.token-url",() -> base+"/token");
    }
    @AfterAll static void close() { server.stop(0); }
    @BeforeEach void setup() { returns.deleteAll(); allocations.deleteAll(); items.deleteAll(); sellers.deleteAll(); orders.deleteAll(); reserves.set(0); failNext.set(false); }
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
}
