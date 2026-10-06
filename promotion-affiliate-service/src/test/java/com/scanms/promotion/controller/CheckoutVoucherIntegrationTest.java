package com.scanms.promotion.controller;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.constant.*;
import com.scanms.promotion.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:checkout-vouchers;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN","scanms.finance.client-secret="})
class CheckoutVoucherIntegrationTest {
    @Autowired javax.sql.DataSource migrationDataSource;
    @Test void postgresMigrationsCanBeAppliedTwice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SCANMS_TEST_POSTGRES_PREFIX")!=null);
        try(var connection=migrationDataSource.getConnection();var statement=connection.createStatement()) {
            for(int pass=0;pass<2;pass++) {
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","finance-finalization-upgrade.sql")));
            }
        }
    }

    // Optional real PostgreSQL run; only explicitly named QA databases may be dropped by Hibernate.
    @org.springframework.test.context.DynamicPropertySource
    static void postgresDatabase(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix=System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if(prefix==null || prefix.isBlank()) return;
        if(!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_")) throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        r.add("spring.datasource.url",() -> prefix+"promotion");
        r.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        r.add("spring.datasource.username",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER","scanms_qa"));
        r.add("spring.datasource.password",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD",""));
    }

    @Autowired CheckoutVoucherController checkout;
    @Autowired VoucherRepository vouchers;
    @Autowired FinanceContractController finance;
    @Autowired CommissionRepository commissions;
    @Autowired CommissionFinalizationRepository finalizations;
    @Autowired com.scanms.promotion.service.CommissionService commissionService;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JwtDecoder decoder;
    final Jwt jwt=Jwt.withTokenValue("internal").header("alg","RS256").subject("order-service").claim("realm_access",Map.of("roles",List.of("ORDER_INTERNAL","PAYMENT_INTERNAL"))).build();
    static final java.util.concurrent.atomic.AtomicBoolean attributed=new java.util.concurrent.atomic.AtomicBoolean();
    static final com.sun.net.httpserver.HttpServer source=source();
    static com.sun.net.httpserver.HttpServer source() {
        try {
            var s=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            s.createContext("/api/v1/finance/commission-order-item/item",exchange -> { byte[] body=("{\"result\":{\"basisAmountVnd\":900,\"commissionApplicable\":"+attributed.get()+"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); }); s.start(); return s;
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void sources(DynamicPropertyRegistry r) { r.add("clients.order.url",() -> "http://127.0.0.1:"+source.getAddress().getPort()); }
    @AfterAll static void close() { source.stop(0); }
    @BeforeEach void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> em.createQuery("delete from CheckoutVoucher").executeUpdate()); vouchers.deleteAll(); commissions.deleteAll(); finalizations.deleteAll(); attributed.set(false);
        vouchers.saveAndFlush(Voucher.builder().code("SAVE").issuerType(VoucherIssuerType.PLATFORM).scopeType(VoucherScopeType.PLATFORM).discountType(VoucherDiscountType.FIXED).value(BigDecimal.valueOf(99)).validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)).validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1)).limits(Map.of("perCustomerLimit",1)).build());
    }
    CheckoutVoucherController.Input input() {
        List<Map<String,Object>> lines=new ArrayList<>(); for(int i=0;i<100;i++) lines.add(Map.of("variantId","v"+i,"storeId","store","productId","product","grossAmountVnd",1));
        return new CheckoutVoucherController.Input("SAVE","customer",lines);
    }
    @Test @SuppressWarnings("unchecked") void allocationsNeverExceedLineValueAndPreserveEveryVnd() {
        var result=checkout.quote(input(),jwt).result(); var lines=(List<Map<String,Object>>)result.get("allocations"); long sum=0;
        for(var line:lines) { long amount=((Number)line.get("allocatedAmountVnd")).longValue(); assertTrue(amount>=0 && amount<=1); sum+=amount; }
        assertEquals(99,sum);
    }
    @Test void usageLimitAndRetryAreAtomicAndReleasedVoucherCanBeUsedAgain() {
        checkout.reserve("first",input(),jwt); checkout.reserve("first",input(),jwt);
        assertThrows(ResponseStatusException.class,() -> checkout.reserve("second",input(),jwt));
        checkout.release("first",jwt); assertDoesNotThrow(() -> checkout.reserve("second",input(),jwt));
        checkout.commit("second",jwt); checkout.release("second",jwt);
        assertThrows(ResponseStatusException.class,() -> checkout.reserve("third",input(),jwt));
    }
    @Test void missingCommissionIsUnknownUntilSourceExplicitlyFinalizesZero() {
        assertThrows(ResponseStatusException.class,() -> finance.total("item",jwt));
        finance.finalizeItem("item",new FinanceContractController.FinalizeInput("No referral attribution"),jwt);
        assertEquals(0L,((Number)finance.total("item",jwt).result().get("amountVnd")).longValue());
        assertEquals(true,finance.total("item",jwt).result().get("finalized"));
    }
    @Test void attributedItemCannotBeFinalizedWithoutItsCommissionCalculation() {
        attributed.set(true);
        assertThrows(ResponseStatusException.class,() -> finance.finalizeItem("item",new FinanceContractController.FinalizeInput("Return window closed"),jwt));
        assertTrue(finalizations.findById("item").isEmpty());
    }
    @Test void partialReturnUpdatesCommissionBeforeFinalizationAndPreventsLateInsert() {
        attributed.set(true);
        commissions.saveAndFlush(Commission.builder().orderItemId("item").collaboratorId("owner").rateSnapshot(BigDecimal.TEN).basisAmountVnd(1000L).commissionAmountVnd(100L).status(CommissionStatus.PENDING).build());
        finance.finalizeItem("item",new FinanceContractController.FinalizeInput("Finalize after partial refund"),jwt);
        assertEquals(90L,((Number)finance.total("item",jwt).result().get("amountVnd")).longValue());
        assertEquals(CommissionStatus.ELIGIBLE,commissions.findAll().getFirst().getStatus());
        assertThrows(com.scanms.promotion.exception.AppException.class,() -> commissionService.create(new com.scanms.promotion.dto.request.CreateCommissionRequest("item","owner",null,null,1000L,BigDecimal.TEN,100L,CommissionStatus.PENDING,null,null,null)));
    }
}
