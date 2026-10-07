package com.scanms.product.controller;
import com.scanms.product.entity.*;
import com.scanms.product.constant.*;
import com.scanms.product.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventory-checkout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN"})
class InventoryCheckoutIntegrationTest {
    @Autowired javax.sql.DataSource migrationDataSource;
    @Test void postgresMigrationsCanBeAppliedTwice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SCANMS_TEST_POSTGRES_PREFIX")!=null);
        try(var connection=migrationDataSource.getConnection();var statement=connection.createStatement()) {
            for(int pass=0;pass<2;pass++) {
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","checkout-reservations.sql")));
            }
        }
    }

    // Optional real PostgreSQL run; only explicitly named QA databases may be dropped by Hibernate.
    @org.springframework.test.context.DynamicPropertySource
    static void postgresDatabase(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix=System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if(prefix==null || prefix.isBlank()) return;
        if(!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_")) throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        r.add("spring.datasource.url",() -> prefix+"product");
        r.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        r.add("spring.datasource.username",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER","scanms_qa"));
        r.add("spring.datasource.password",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD",""));
    }

    @Autowired InventoryCheckoutController inventory;
    @Autowired ProductRepository products;
    @Autowired StoreRepository stores;
    @Autowired ProductVariantRepository variants;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JwtDecoder decoder;
    final Jwt jwt=Jwt.withTokenValue("internal").header("alg","RS256").subject("order-service").claim("realm_access",Map.of("roles",List.of("ORDER_INTERNAL"))).build();
    String productId,variantId;
    @BeforeEach void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> em.createQuery("delete from CheckoutReservation").executeUpdate());
        variants.deleteAll(); products.deleteAll(); stores.deleteAll();
        Store store=stores.saveAndFlush(Store.builder().ownerUserId("owner").name("Shop").approvalStatus(StoreStatus.APPROVED).build());
        Product product=products.saveAndFlush(Product.builder().storeId(store.getStoreId()).name("Product").priceVnd(100L).status(ProductStatus.ACTIVE).build()); productId=product.getProductId();
        variantId=variants.saveAndFlush(ProductVariant.builder().productId(productId).sku("sku").priceVnd(100L).stockQuantity(3).status(ProductVariantStatus.ACTIVE).build()).getVariantId();
    }
    InventoryCheckoutController.Input input(int quantity) { return new InventoryCheckoutController.Input(List.of(new InventoryCheckoutController.Line(productId,variantId,quantity))); }
    int stock() { return variants.findById(variantId).orElseThrow().getStockQuantity(); }
    @Test void reserveAndReleaseAreIdempotentAndChangedPayloadIsRejected() {
        inventory.reserve("order",input(2),jwt); inventory.reserve("order",input(2),jwt); assertEquals(1,stock());
        assertThrows(ResponseStatusException.class,() -> inventory.reserve("order",input(1),jwt));
        inventory.release("order",jwt); inventory.release("order",jwt); assertEquals(3,stock());
    }
    @Test void concurrentCheckoutsCannotOversell() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1); List<Future<Boolean>> results=new ArrayList<>();
            for(String order:List.of("a","b")) results.add(executor.submit(() -> { start.await(); try { inventory.reserve(order,input(2),jwt); return true; } catch(ResponseStatusException ex) { return false; } }));
            start.countDown(); int successes=0; for(var result:results) if(result.get(15,TimeUnit.SECONDS)) successes++; assertEquals(1,successes); assertEquals(1,stock());
        }
    }
    @Test void paidStockCannotBeReleasedAndQuoteDoesNotReserve() {
        inventory.quote(input(2),jwt); assertEquals(3,stock());
        inventory.reserve("paid",input(2),jwt); inventory.commit("paid",jwt);
        assertThrows(ResponseStatusException.class,() -> inventory.release("paid",jwt)); assertEquals(1,stock());
    }
    @Test void customerCannotMutateStockEvenWithAnOrderReference() {
        Jwt customer=Jwt.withTokenValue("user").header("alg","RS256").subject("customer").claim("realm_access",Map.of("roles",List.of("USER"))).build();
        assertThrows(ResponseStatusException.class,() -> inventory.reserve("order",input(2),customer)); assertEquals(3,stock());
    }
}
