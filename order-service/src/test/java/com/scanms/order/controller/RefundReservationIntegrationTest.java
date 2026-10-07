package com.scanms.order.controller;
import com.scanms.order.constant.*;
import com.scanms.order.entity.*;
import com.scanms.order.entity.Order;
import com.scanms.order.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:refund-reservations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN","scanms.finance.client-secret="})
class RefundReservationIntegrationTest {
    // Optional real PostgreSQL run; only explicitly named QA databases may be dropped by Hibernate.
    @org.springframework.test.context.DynamicPropertySource
    static void postgresDatabase(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix=System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if(prefix==null || prefix.isBlank()) return;
        if(!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_")) throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        r.add("spring.datasource.url",() -> prefix+"refunds");
        r.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        r.add("spring.datasource.username",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER","scanms_qa"));
        r.add("spring.datasource.password",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD",""));
    }

    @Autowired FinanceContractController finance;
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderItemRepository items;
    @Autowired ReturnRequestRepository returns;
    @Autowired DiscountAllocationRepository allocations;
    @MockitoBean JwtDecoder decoder;
    final Jwt jwt=Jwt.withTokenValue("internal").header("alg","RS256").subject("source").claim("realm_access",Map.of("roles",List.of("PAYMENT_INTERNAL"))).build();
    String itemId;
    @BeforeEach void setup() {
        returns.deleteAll(); allocations.deleteAll(); items.deleteAll(); sellers.deleteAll(); orders.deleteAll();
        Order order=orders.saveAndFlush(Order.builder().customerId("customer").payableVnd(100L).currency("VND").status(OrderStatus.PAID).build());
        SellerOrder seller=sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId("store").status(SellerOrderStatus.DELIVERED).deliveredAt(LocalDateTime.now().minusDays(5)).returnDeadline(LocalDateTime.now().plusDays(9)).build());
        itemId=items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).quantity(3).grossAmountVnd(100L).discountAmountVnd(0L).netPaidAmountVnd(100L).build()).getOrderItemId();
    }
    ReturnRequest request(int quantity,ReturnRequestStatus status,int offset) {
        return returns.saveAndFlush(ReturnRequest.builder().orderItemId(itemId).quantity(quantity).status(status).requestedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10).plusSeconds(offset)).build());
    }
    long basis(String id) { return ((Number)finance.refundBasis(id,jwt).result().get("amountVnd")).longValue(); }
    @Test void cancellationBeforeAnAlreadyReservedRefundDoesNotShiftMoneyOrOverRefund() {
        var a=request(1,ReturnRequestStatus.REQUESTED,0); var b=request(2,ReturnRequestStatus.APPROVED,1);
        long bAmount=basis(b.getReturnRequestId()); assertEquals(66,bAmount);
        finance.refunded(b.getReturnRequestId(),new FinanceContractController.RefundInput("ledger-b",bAmount),jwt);
        a.setStatus(ReturnRequestStatus.CANCELLED); returns.saveAndFlush(a);
        assertEquals(bAmount,basis(b.getReturnRequestId()));
        var c=request(1,ReturnRequestStatus.APPROVED,2); long cAmount=basis(c.getReturnRequestId());
        assertEquals(34,cAmount); assertEquals(100,bAmount+cAmount);
    }
    @Test void pendingSnapshotSurvivesCancellationWhilePaymentAcknowledgementIsDelayed() {
        var a=request(1,ReturnRequestStatus.REQUESTED,0); var b=request(2,ReturnRequestStatus.APPROVED,1);
        long amount=basis(b.getReturnRequestId()); a.setStatus(ReturnRequestStatus.REJECTED); returns.saveAndFlush(a);
        assertEquals(amount,basis(b.getReturnRequestId()));
        finance.refunded(b.getReturnRequestId(),new FinanceContractController.RefundInput("ledger",amount),jwt);
        assertEquals(ReturnRequestStatus.REFUNDED,returns.findById(b.getReturnRequestId()).orElseThrow().getStatus());
    }
    @Test void simultaneousReservationsCannotExceedPurchasedQuantity() throws Exception {
        var a=request(2,ReturnRequestStatus.APPROVED,0); var b=request(2,ReturnRequestStatus.APPROVED,1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1); List<Future<Boolean>> results=new ArrayList<>();
            for(var r:List.of(a,b)) results.add(executor.submit(() -> { start.await(); try { basis(r.getReturnRequestId()); return true; } catch(ResponseStatusException ex) { return false; } }));
            start.countDown(); int successes=0; for(var result:results) if(result.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }
    }
}
