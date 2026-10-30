package com.scanms.promotion.controller;
import com.scanms.promotion.dto.request.CheckoutVoucherInput;
import com.scanms.promotion.dto.request.FinalizeInput;
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
    @Autowired VoucherRedemptionRepository redemptions;
    @Autowired FinanceContractController finance;
    @Autowired CommissionRepository commissions;
    @Autowired CommissionFinalizationRepository finalizations;
    @Autowired CollaboratorProfileRepository profiles;
    @Autowired ReferralLinkRepository links;
    @Autowired CommissionPolicyRepository policies;
    @Autowired ProductCommissionDealRepository productDeals;
    @Autowired com.scanms.promotion.service.CommissionService commissionService;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JwtDecoder decoder;
    final Jwt jwt=Jwt.withTokenValue("internal").header("alg","RS256").subject("order-service").claim("realm_access",Map.of("roles",List.of("ORDER_INTERNAL","PAYMENT_INTERNAL"))).build();
    static final java.util.concurrent.atomic.AtomicReference<Instant> orderPlacedAt=new java.util.concurrent.atomic.AtomicReference<>(Instant.now());
    static final java.util.concurrent.atomic.AtomicReference<String> referralId=new java.util.concurrent.atomic.AtomicReference<>("link");
    static final java.util.concurrent.atomic.AtomicBoolean attributed=new java.util.concurrent.atomic.AtomicBoolean();
    static final java.util.concurrent.atomic.AtomicLong retained=new java.util.concurrent.atomic.AtomicLong(900);
    static final java.util.concurrent.atomic.AtomicBoolean restorationAllowed=new java.util.concurrent.atomic.AtomicBoolean(true);
    static final com.sun.net.httpserver.HttpServer source=source();
    static com.sun.net.httpserver.HttpServer source() {
        try {
            var s=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            s.createContext("/api/v1/finance/commission-order-item/item",exchange -> { byte[] body=("{\"result\":{\"basisAmountVnd\":"+retained.get()+",\"refundedAmountVnd\":"+(1000-retained.get())+",\"commissionApplicable\":"+attributed.get()+"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            s.createContext("/api/v1/finance/commission-adjustment-item/item",exchange -> { byte[] body=("{\"result\":{\"basisAmountVnd\":"+retained.get()+",\"refundedAmountVnd\":"+(1000-retained.get())+"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            s.createContext("/api/v1/order-items/item",exchange -> { byte[] body=("{\"result\":{\"referralLinkId\":\""+referralId.get()+"\",\"sellerOrderId\":\"seller\",\"netPaidAmountVnd\":1000,\"placedAt\":\""+orderPlacedAt.get()+"\",\"productSnapshot\":{\"name\":\"Test shirt\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            s.createContext("/api/v1/seller-orders/seller",exchange -> { byte[] body="{\"result\":{\"storeId\":\"store\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            s.createContext("/api/v1/orders/restorable/voucher-restoration-eligibility",exchange -> { byte[] body=("{\"result\":{\"eligible\":"+restorationAllowed.get()+"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); });
            s.start(); return s;
        } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void sources(DynamicPropertyRegistry r) { r.add("clients.order.url",() -> "http://127.0.0.1:"+source.getAddress().getPort()); }
    @AfterAll static void close() { source.stop(0); }
    @BeforeEach void setup() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> em.createQuery("delete from CheckoutVoucher").executeUpdate()); productDeals.deleteAll(); orderPlacedAt.set(Instant.now()); redemptions.deleteAll(); vouchers.deleteAll(); commissions.deleteAll(); finalizations.deleteAll(); links.deleteAll(); profiles.deleteAll(); policies.deleteAll(); attributed.set(false); retained.set(900);
        vouchers.saveAndFlush(Voucher.builder().code("SAVE").issuerType(VoucherIssuerType.PLATFORM).scopeType(VoucherScopeType.PLATFORM).discountType(VoucherDiscountType.FIXED).value(BigDecimal.valueOf(99)).validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)).validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1)).limits(Map.of("perCustomerLimit",1)).build());
    }
    CheckoutVoucherInput input() {
        List<Map<String,Object>> lines=new ArrayList<>(); for(int i=0;i<100;i++) lines.add(Map.of("variantId","v"+i,"storeId","store","productId","product","grossAmountVnd",1));
        return new CheckoutVoucherInput("SAVE","customer",lines);
    }

    void shippingVoucher() {
        vouchers.saveAndFlush(Voucher.builder().code("SHIP").issuerType(VoucherIssuerType.PLATFORM).scopeType(VoucherScopeType.PLATFORM)
                .scope(Map.of("benefitType","SHIPPING")).discountType(VoucherDiscountType.FIXED).value(BigDecimal.valueOf(50000))
                .validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)).validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
                .limits(Map.of("totalUsageLimit",1,"perCustomerLimit",1)).build());
    }

    @Test void carrierFeesAreAllocatedByEachStoreFeeWithoutTrustingBrowserOrExceedingFees() {
        shippingVoucher();
        var lines=List.<Map<String,Object>>of(Map.of("variantId","a","storeId","shop-a","productId","a","grossAmountVnd",100000),Map.of("variantId","b","storeId","shop-b","productId","b","grossAmountVnd",100000));
        var fees=Map.of("shop-a",25000L,"shop-b",35000L);
        var command=new CheckoutVoucherController.Input("SHIP","customer",lines,null,fees);
        var result=checkout.quote(command,jwt,null).result();
        assertEquals(50000L,result.get("discountAmountVnd"));
        @SuppressWarnings("unchecked") var shares=(List<Map<String,Object>>)result.get("allocations");
        assertEquals(50000L,shares.stream().mapToLong(row->((Number)row.get("allocatedAmountVnd")).longValue()).sum());
        for(var row:shares)assertTrue(((Number)row.get("allocatedAmountVnd")).longValue()<=fees.get(row.get("storeId")));
        checkout.reserve("ghn-shipping-voucher",command,jwt,null);checkout.reserve("ghn-shipping-voucher",command,jwt,null);
        var changed=new CheckoutVoucherController.Input("SHIP","customer",lines,null,Map.of("shop-a",30000L,"shop-b",35000L));
        assertThrows(ResponseStatusException.class,()->checkout.reserve("ghn-shipping-voucher",changed,jwt,null));
        var missing=new CheckoutVoucherController.Input("SHIP","customer",lines,null,Map.of("shop-a",25000L));
        assertThrows(ResponseStatusException.class,()->checkout.quote(missing,jwt,null));
    }

    @Test void pairReservesExactlyTwoRedemptionsAndReleasesBothWithoutLosingQuota() {
        shippingVoucher();
        var pair=new CheckoutVoucherController.Input("SAVE","customer",input().lines(),"SHIP");
        assertEquals(30099L,checkout.quote(pair,jwt,30000L).result().get("discountAmountVnd"));
        assertTrue(redemptions.findAll().isEmpty());
        checkout.reserve("pair",pair,jwt,30000L); checkout.reserve("pair",pair,jwt,30000L);
        assertEquals(2,redemptions.findAllByOrderId("pair").size());
        assertThrows(ResponseStatusException.class,()->checkout.reserve("other",pair,jwt,30000L));
        assertThrows(ResponseStatusException.class,()->checkout.reserve("pair",pair,jwt,40000L));
        checkout.release("pair",jwt); checkout.release("pair",jwt);
        assertTrue(redemptions.findAllByOrderId("pair").stream().allMatch(r->r.getStatus()==VoucherRedemptionStatus.CANCELLED));
        checkout.reserve("next-pair",pair,jwt,30000L); checkout.commit("next-pair",jwt); checkout.commit("next-pair",jwt);
        assertTrue(redemptions.findAllByOrderId("next-pair").stream().allMatch(r->r.getStatus()==VoucherRedemptionStatus.USED));
        assertThrows(ResponseStatusException.class,()->checkout.release("next-pair",jwt));
    }

    @Test void pairRestoresBothUsesOnlyAfterOrderEligibilityAndRejectsWrongBenefitSlots() {
        shippingVoucher();
        var pair=new CheckoutVoucherController.Input("SAVE","customer",input().lines(),"SHIP");
        assertThrows(ResponseStatusException.class,()->checkout.reserve("wrong",new CheckoutVoucherController.Input("SHIP","customer",input().lines(),"SAVE"),jwt,30000L));
        assertThrows(ResponseStatusException.class,()->checkout.reserve("same",new CheckoutVoucherController.Input("SAVE","customer",input().lines(),"save"),jwt,30000L));
        assertTrue(redemptions.findAll().isEmpty());
        checkout.reserve("restorable",pair,jwt,30000L); checkout.commit("restorable",jwt);
        checkout.restore("restorable",jwt); checkout.restore("restorable",jwt);
        assertEquals(2,redemptions.findAllByOrderId("restorable").size());
        assertTrue(redemptions.findAllByOrderId("restorable").stream().allMatch(r->r.getStatus()==VoucherRedemptionStatus.RESTORED));
        assertDoesNotThrow(()->checkout.reserve("restored-pair",pair,jwt,30000L));
    }

    @Test void invalidSecondVoucherRollsBackTheWholePairAndShippingCanBeUsedAlone() {
        shippingVoucher();
        assertThrows(ResponseStatusException.class,()->checkout.reserve("invalid",new CheckoutVoucherController.Input("MISSING","customer",input().lines(),"SHIP"),jwt,30000L));
        assertTrue(redemptions.findAll().isEmpty());
        assertEquals(30000L,checkout.quote(new CheckoutVoucherController.Input("","customer",input().lines(),"SHIP"),jwt,30000L).result().get("discountAmountVnd"));
        checkout.reserve("shipping-only",new CheckoutVoucherController.Input("","customer",input().lines(),"SHIP"),jwt,30000L);
        assertEquals(1,redemptions.findAllByOrderId("shipping-only").size());
    }

    @Test @SuppressWarnings("unchecked") void freeshipIsCappedByShippingAndNeverAllocatedToProductPrices() {
        vouchers.saveAndFlush(Voucher.builder().code("SHIP").issuerType(VoucherIssuerType.PLATFORM)
                .scopeType(VoucherScopeType.PLATFORM).scope(Map.of("benefitType","SHIPPING"))
                .discountType(VoucherDiscountType.FIXED).value(BigDecimal.valueOf(100000)).cap(50000L)
                .minimumSubtotal(100L).validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1)).build());
        var input=new CheckoutVoucherController.Input("SHIP","customer",List.of(
                Map.of("variantId","a","storeId","store-a","productId","p-a","grossAmountVnd",200),
                Map.of("variantId","b","storeId","store-b","productId","p-b","grossAmountVnd",300)));
        var result=checkout.quote(input,jwt,30000L).result();
        var rows=(List<Map<String,Object>>)result.get("allocations");
        assertEquals(50000L,result.get("discountAmountVnd"));
        assertEquals(2,rows.size());
        for(var row:rows) { assertEquals("SHIPPING",row.get("benefitType")); assertFalse(row.containsKey("variantId")); assertEquals(25000L,row.get("allocatedAmountVnd")); }
        checkout.reserve("ship-order",input,jwt,30000L); checkout.reserve("ship-order",input,jwt,30000L);
        assertEquals(1,redemptions.findAll().size());
        assertThrows(ResponseStatusException.class,()->checkout.reserve("ship-order",input,jwt,40000L));
        assertThrows(ResponseStatusException.class,()->checkout.quote(input,jwt,0L));
        checkout.release("ship-order",jwt);
        assertEquals(10000L,checkout.quote(input,jwt,5000L).result().get("discountAmountVnd"));
    }
    @Test @SuppressWarnings("unchecked") void allocationsNeverExceedLineValueAndPreserveEveryVnd() {
        var result=checkout.quote(input(),jwt).result(); var lines=(List<Map<String,Object>>)result.get("allocations"); long sum=0;
        for(var line:lines) { long amount=((Number)line.get("allocatedAmountVnd")).longValue(); assertTrue(amount>=0 && amount<=1); sum+=amount; }
        assertEquals(99,sum);
    }
    @Test void usageLimitAndRetryAreAtomicAndReleasedVoucherCanBeUsedAgain() {
        checkout.reserve("first",input(),jwt); checkout.reserve("first",input(),jwt);
        assertEquals(VoucherRedemptionStatus.RESERVED, redemptions.findByOrderId("first").orElseThrow().getStatus());
        assertNull(redemptions.findByOrderId("first").orElseThrow().getRedeemedAt());
        assertThrows(ResponseStatusException.class,() -> checkout.reserve("second",input(),jwt));
        checkout.release("first",jwt); checkout.release("first",jwt);
        assertEquals(VoucherRedemptionStatus.CANCELLED, redemptions.findByOrderId("first").orElseThrow().getStatus());
        assertDoesNotThrow(() -> checkout.reserve("second",input(),jwt));
        checkout.commit("second",jwt); checkout.commit("second",jwt);
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> checkout.release("second",jwt)).getStatusCode().value());
        assertEquals(VoucherRedemptionStatus.USED, redemptions.findByOrderId("second").orElseThrow().getStatus());
        assertNotNull(redemptions.findByOrderId("second").orElseThrow().getRedeemedAt());
        assertThrows(ResponseStatusException.class,() -> checkout.reserve("third",input(),jwt));
    }
    @Test void commitRequiresARealReservationAndRedemption() {
        assertEquals(404,assertThrows(ResponseStatusException.class,() -> checkout.commit("missing",jwt)).getStatusCode().value());
        checkout.reserve("orphan",input(),jwt);
        redemptions.deleteById(redemptions.findByOrderId("orphan").orElseThrow().getRedemptionId());
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> checkout.commit("orphan",jwt)).getStatusCode().value());
    }
    @Test @SuppressWarnings("unchecked") void storeFundingMatchesOrderContractAndLivestreamNeedsAttribution() {
        vouchers.saveAndFlush(Voucher.builder().code("STORE").issuerType(VoucherIssuerType.STORE).storeId("store")
                .scopeType(VoucherScopeType.STORE).discountType(VoucherDiscountType.FIXED).value(BigDecimal.TEN)
                .validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)).validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1)).build());
        var result=checkout.quote(new CheckoutVoucherInput("STORE","customer",input().lines()),jwt).result();
        var allocations=(List<Map<String,Object>>)result.get("allocations");
        assertEquals("STORE",allocations.getFirst().get("fundedBy"));
        vouchers.saveAndFlush(Voucher.builder().code("LIVE").issuerType(VoucherIssuerType.STORE).storeId("store")
                .livestreamId("live-1").scopeType(VoucherScopeType.LIVESTREAM).discountType(VoucherDiscountType.FIXED).value(BigDecimal.TEN)
                .validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)).validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1)).build());
        assertThrows(ResponseStatusException.class,() -> checkout.quote(new CheckoutVoucherInput("LIVE","customer",input().lines()),jwt));
        var attributed=List.<Map<String,Object>>of(Map.of("variantId","v1","storeId","store","productId","product","sourceLivestreamId","live-1","grossAmountVnd",100));
        assertEquals(10L,checkout.quote(new CheckoutVoucherInput("LIVE","customer",attributed),jwt).result().get("discountAmountVnd"));
    }
    @Test void committedVoucherCanOnlyBeRestoredAfterOrderConfirmsPreDeliveryCancellation() {
        restorationAllowed.set(true);
        checkout.reserve("restorable",input(),jwt);
        checkout.commit("restorable",jwt);
        checkout.restore("restorable",jwt);
        assertEquals(VoucherRedemptionStatus.RESTORED,redemptions.findByOrderId("restorable").orElseThrow().getStatus());
        assertDoesNotThrow(() -> checkout.restore("restorable",jwt));
        assertDoesNotThrow(() -> checkout.reserve("next",input(),jwt));
    }
    @Test void cancellationRestoresOnlyTheStillValidVoucherInAPairAndIsIdempotent(){
        restorationAllowed.set(true);
        var ship=vouchers.saveAndFlush(Voucher.builder().code("SHIP-PAIR").issuerType(VoucherIssuerType.PLATFORM).scopeType(VoucherScopeType.PLATFORM)
                .discountType(VoucherDiscountType.FIXED).value(BigDecimal.valueOf(50)).scope(Map.of("benefitType","SHIPPING"))
                .validFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)).validUntil(LocalDateTime.now(ZoneOffset.UTC).plusDays(1)).build());
        var pair=new CheckoutVoucherController.Input("SAVE","customer",input().lines(),"SHIP-PAIR");
        checkout.reserve("restorable",pair,jwt,30000L);checkout.commit("restorable",jwt);
        restorationAllowed.set(false);assertThrows(ResponseStatusException.class,()->checkout.cancel("restorable",jwt));
        assertTrue(redemptions.findAllByOrderId("restorable").stream().allMatch(r->r.getStatus()==VoucherRedemptionStatus.USED));
        restorationAllowed.set(true);ship.setValidUntil(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));vouchers.saveAndFlush(ship);
        checkout.cancel("restorable",jwt);checkout.cancel("restorable",jwt);
        var rows=redemptions.findAllByOrderId("restorable");assertEquals(2,rows.size());
        assertEquals(VoucherRedemptionStatus.CANCELLED,rows.stream().filter(r->r.getVoucherId().equals(ship.getVoucherId())).findFirst().orElseThrow().getStatus());
        assertEquals(1,rows.stream().filter(r->r.getStatus()==VoucherRedemptionStatus.RESTORED).count());
        assertDoesNotThrow(()->checkout.reserve("next-valid-code",input(),jwt));
        assertThrows(ResponseStatusException.class,()->checkout.reserve("expired-ship",pair,jwt,30000L));
    }
    @Test void missingCommissionIsUnknownUntilSourceExplicitlyFinalizesZero() {
        assertThrows(ResponseStatusException.class,() -> finance.total("item",jwt));
        finance.finalizeItem("item",new FinalizeInput("No referral attribution"),jwt);
        assertEquals(0L,((Number)finance.total("item",jwt).result().get("amountVnd")).longValue());
        assertEquals(true,finance.total("item",jwt).result().get("finalized"));
    }
    @Test void attributedItemCannotBeFinalizedWithoutItsCommissionCalculation() {
        attributed.set(true);
        assertThrows(ResponseStatusException.class,() -> finance.finalizeItem("item",new FinalizeInput("Return window closed"),jwt));
        assertTrue(finalizations.findById("item").isEmpty());
    }
    @Test void partialReturnUpdatesCommissionBeforeFinalizationAndPreventsLateInsert() {
        attributed.set(true);
        commissions.saveAndFlush(Commission.builder().orderItemId("item").collaboratorId("owner").rateSnapshot(BigDecimal.TEN).basisAmountVnd(1000L).commissionAmountVnd(100L).status(CommissionStatus.PENDING).build());
        finance.finalizeItem("item",new FinalizeInput("Finalize after partial refund"),jwt);
        assertEquals(90L,((Number)finance.total("item",jwt).result().get("amountVnd")).longValue());
        assertEquals(CommissionStatus.ELIGIBLE,commissions.findAll().getFirst().getStatus());
        assertThrows(com.scanms.promotion.exception.AppException.class,() -> commissionService.create(new com.scanms.promotion.dto.request.CreateCommissionRequest("item","owner",null,null,1000L,BigDecimal.TEN,100L,CommissionStatus.PENDING,null,null,null)));
    }
    @Test void finalizationKeepsOriginalAndRefundSnapshotWhenAdjustmentWorkerWasDelayed() {
        commissions.saveAndFlush(Commission.builder().orderItemId("item").collaboratorId("owner").rateSnapshot(BigDecimal.TEN).basisAmountVnd(1000L).commissionAmountVnd(100L).status(CommissionStatus.PENDING).build());
        retained.set(0);
        finance.finalizeItem("item",new FinanceContractController.FinalizeInput("Refund completed before delayed adjustment"),jwt);
        var row=commissions.findAll().getFirst();
        assertEquals(CommissionStatus.CANCELLED,row.getStatus()); assertEquals(0L,row.getCommissionAmountVnd());
        assertEquals(100L,((Number)row.getAdjustmentHistory().get("originalAmountVnd")).longValue());
        assertEquals(1000L,((Number)row.getAdjustmentHistory().get("refundedAmountVnd")).longValue());
        finance.adjustCommission("item",new FinanceContractController.AdjustInput("Delayed retry"),jwt);
        assertEquals(0L,finance.total("item",jwt).result().get("amountVnd"));
    }
    @Test void referralPaymentInitializesPendingAmountAndFullRefundCancelsIt() {
        var profile=profiles.saveAndFlush(CollaboratorProfile.builder().userId("creator").approvalStatus(CollaboratorStatus.APPROVED).build());
        var link=links.saveAndFlush(ReferralLink.builder().collaboratorId(profile.getCollaboratorId()).productId("product").token("ref").status(ReferralLinkStatus.ACTIVE).build());
        referralId.set(link.getReferralLinkId());
        policies.saveAndFlush(CommissionPolicy.builder().policyName("Platform 10%").scopeType(CommissionPolicyScope.PLATFORM).commissionRate(BigDecimal.TEN).status(CommissionPolicyStatus.ACTIVE).validFrom(Instant.now().minusSeconds(60)).returnWindowDays(14).priority(0).build());
        finance.initializeCommission("item",new FinanceContractController.InitInput("Payment confirmed"),jwt);
        finance.initializeCommission("item",new FinanceContractController.InitInput("Retry"),jwt);
        assertEquals(1,commissions.count()); var row=commissions.findAll().getFirst();
        assertEquals(100L,row.getCommissionAmountVnd()); assertEquals(CommissionStatus.PENDING,row.getStatus());
        String commissionId = row.getCommissionId();
        assertThrows(ResponseStatusException.class,()->finance.basis(commissionId,jwt));
        retained.set(0); finance.adjustCommission("item",new FinanceContractController.AdjustInput("Full return refunded"),jwt);
        row=commissions.findAll().getFirst(); assertEquals(0L,row.getCommissionAmountVnd()); assertEquals(CommissionStatus.CANCELLED,row.getStatus());
        attributed.set(true); finance.finalizeItem("item",new FinanceContractController.FinalizeInput("Return window closed"),jwt);
        assertEquals(0L,finance.total("item",jwt).result().get("amountVnd"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void approvedProductDealSnapshotsThirteenAndFullRefundCancelsWithoutChangingRate(boolean alreadyCredited) {
        var profile=profiles.saveAndFlush(CollaboratorProfile.builder().userId("creator").approvalStatus(CollaboratorStatus.APPROVED).build());
        var link=links.saveAndFlush(ReferralLink.builder().collaboratorId(profile.getCollaboratorId()).productId("product").token("deal-ref").status(ReferralLinkStatus.ACTIVE).build());
        referralId.set(link.getReferralLinkId());
        var policy=policies.saveAndFlush(CommissionPolicy.builder().policyName("Platform 10%").scopeType(CommissionPolicyScope.PLATFORM).commissionRate(BigDecimal.TEN).status(CommissionPolicyStatus.ACTIVE).validFrom(Instant.now().minusSeconds(120)).returnWindowDays(14).priority(0).build());
        var deal=new ProductCommissionDeal(); deal.setCollaboratorId(profile.getCollaboratorId());deal.setProductId("product");deal.setStoreId("store");deal.setProductName("Test shirt");deal.setExtraPoints(3);deal.setBaseRate(BigDecimal.TEN);deal.setBasePolicyId(policy.getPolicyId());deal.setTotalRate(BigDecimal.valueOf(13));deal.setMessage("Product agreement");deal.setStatus("APPROVED");deal.setCreatedAt(Instant.now().minusSeconds(90));deal.setApprovedAt(Instant.now().minusSeconds(60));productDeals.saveAndFlush(deal);
        finance.initializeCommission("item",new FinanceContractController.InitInput("Paid"),jwt);
        var row=commissions.findAll().getFirst();
        assertEquals(0,row.getRateSnapshot().compareTo(BigDecimal.valueOf(13)));assertEquals(130L,row.getCommissionAmountVnd());
        assertEquals(3,((Number)row.getAdjustmentHistory().get("extraPoints")).intValue());
        assertEquals(deal.getId(),row.getAdjustmentHistory().get("productDealId"));
        if(alreadyCredited) {
            row.setStatus(CommissionStatus.CREDITED);row.setTransferReference("qa-original-ledger");
            var history=new LinkedHashMap<>(row.getAdjustmentHistory());history.put("ledgerReconciledAmountVnd",130L);row.setAdjustmentHistory(history);
            row=commissions.saveAndFlush(row);
        }
        policy.setCommissionRate(BigDecimal.valueOf(30));policies.saveAndFlush(policy);
        deal.setStatus("ENDED");deal.setEndedAt(Instant.now());productDeals.saveAndFlush(deal);
        finance.initializeCommission("item",new FinanceContractController.InitInput("Retry after policy changed"),jwt);
        row=commissions.findById(row.getCommissionId()).orElseThrow();assertEquals(130L,row.getCommissionAmountVnd());
        retained.set(0);finance.adjustCommission("item",new FinanceContractController.AdjustInput("Full refund"),jwt);
        row=commissions.findById(row.getCommissionId()).orElseThrow();assertEquals(0L,row.getCommissionAmountVnd());assertEquals(CommissionStatus.CANCELLED,row.getStatus());
        assertEquals(0,row.getRateSnapshot().compareTo(BigDecimal.valueOf(13)));
        if(alreadyCredited) {
            assertEquals(0L,finance.reconciliationBasis(row.getCommissionId(),jwt).result().get("amountVnd"));
            finance.reconciled(row.getCommissionId(),new FinanceContractController.CreditInput("qa-reversal-ledger",0L),jwt);
            finance.reconciled(row.getCommissionId(),new FinanceContractController.CreditInput("qa-reversal-ledger",0L),jwt);
            assertEquals("qa-original-ledger",commissions.findById(row.getCommissionId()).orElseThrow().getTransferReference());
        }
        finance.adjustCommission("item",new FinanceContractController.AdjustInput("Refund retry"),jwt);
        assertEquals(1,commissions.count());
    }

    @Test void duplicateCreditAcknowledgementKeepsOriginalDateAndNoOpReconciliationHasNoReversal() {
        var paidAt=java.time.LocalDateTime.now(ZoneOffset.UTC).minusDays(2).withNano(0);
        var row=commissions.saveAndFlush(Commission.builder().orderItemId("item").collaboratorId("kol").rateSnapshot(BigDecimal.TEN).basisAmountVnd(1000L).commissionAmountVnd(100L).status(CommissionStatus.CREDITED).transferReference("original-ledger").paidAt(paidAt).build());
        finance.reconciled(row.getCommissionId(),new FinanceContractController.CreditInput("original-ledger",100L),jwt);
        finance.credited(row.getCommissionId(),new FinanceContractController.CreditInput("original-ledger",100L),jwt);
        row=commissions.findById(row.getCommissionId()).orElseThrow();
        assertFalse(row.getAdjustmentHistory().containsKey("reversalTransactionId"));
        assertEquals(paidAt,row.getPaidAt());
    }
    @Test void creditedFullRefundHasZeroTargetAndDelayedAcknowledgementsNeverRestoreCommission() {
        var row=commissions.saveAndFlush(Commission.builder().orderItemId("item").collaboratorId("kol").rateSnapshot(BigDecimal.TEN).basisAmountVnd(1000L).commissionAmountVnd(100L).status(CommissionStatus.CREDITED).transferReference("original-ledger").adjustmentHistory(Map.of("ledgerReconciledAmountVnd",100)).build());
        retained.set(0); finance.adjustCommission("item",new FinanceContractController.AdjustInput("Full return"),jwt);
        assertEquals(0L,finance.reconciliationBasis(row.getCommissionId(),jwt).result().get("amountVnd"));
        finance.reconciled(row.getCommissionId(),new FinanceContractController.CreditInput("reversal-ledger",0L),jwt);
        assertEquals("CANCELLED",finance.credited(row.getCommissionId(),new FinanceContractController.CreditInput("original-ledger",100L),jwt).result().get("status"));
        finance.reconciled(row.getCommissionId(),new FinanceContractController.CreditInput("older-reversal",40L),jwt);
        row=commissions.findById(row.getCommissionId()).orElseThrow();
        assertEquals(CommissionStatus.CANCELLED,row.getStatus()); assertEquals(0L,((Number)row.getAdjustmentHistory().get("ledgerReconciledAmountVnd")).longValue());
        assertEquals("reversal-ledger",row.getAdjustmentHistory().get("reversalTransactionId"));
    }
}
