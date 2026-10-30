package com.scanms.order.controller;

import com.scanms.order.constant.*;
import com.scanms.order.entity.*;
import com.scanms.order.entity.Order;
import com.scanms.order.repository.*;
import com.scanms.order.dto.request.RefundInput;
import com.scanms.order.service.*;
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
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:commerce-lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret=", "scanms.shipping.simulation-enabled=true"})
@org.springframework.test.context.ActiveProfiles("local")
class CommerceLifecycleIntegrationTest {
    @Autowired CommerceLifecycle lifecycle;
    @Autowired FinanceContractController finance;
    @Autowired ManagerReturnController manager;
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderItemRepository items;
    @Autowired ReturnRequestRepository returns;
    @Autowired ShipmentRepository shipments;
    @MockitoBean CommerceAccess access;
    @MockitoBean ManagerStatisticsAccess managers;
    @MockitoBean JwtDecoder decoder;
    final Jwt buyer=jwt("buyer"),shop=jwt("shop"),other=jwt("other"),internal=Jwt.withTokenValue("internal").header("alg","RS256").subject("internal").claim("realm_access",Map.of("roles",List.of("PAYMENT_INTERNAL"))).build();
    static Jwt jwt(String id) { return Jwt.withTokenValue(id).header("alg","RS256").subject(id).build(); }
    Order order; SellerOrder seller; OrderItem item;
    final Map<String,Object> evidence=Map.of("images",List.of("https://example.test/qa.png"),"unboxingVideoUrl","https://example.test/qa.mp4","details","QA damaged item");
    @BeforeEach void setup() {
        returns.deleteAll();shipments.deleteAll();items.deleteAll();sellers.deleteAll();orders.deleteAll();
        when(access.customer(buyer)).thenReturn("buyer");when(access.customer(shop)).thenReturn("shop");when(access.customer(other)).thenReturn("other");
        when(access.stores(shop)).thenReturn(Set.of("store"));when(access.stores(buyer)).thenReturn(Set.of());when(access.stores(other)).thenReturn(Set.of());
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)).when(access).shop(buyer,"store");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)).when(access).shop(other,"store");
        order=orders.saveAndFlush(Order.builder().customerId("buyer").status(OrderStatus.PAID).payableVnd(100L).currency("VND").build());
        seller=sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId("store").status(SellerOrderStatus.PENDING).build());
        item=items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).productId("product").quantity(3).unitPrice(34L).grossAmountVnd(102L).discountAmountVnd(2L).netPaidAmountVnd(100L).build());
    }
    String sid(){return seller.getSellerOrderId();}
    CommerceLifecycle.ReturnInput input(int quantity,String key) { return new CommerceLifecycle.ReturnInput(item.getOrderItemId(),quantity,key,"DAMAGED",evidence); }
    String create(int quantity,String key) { return lifecycle.createReturn(input(quantity,key),buyer).get("returnRequestId").toString(); }
    void delivered() { lifecycle.dispatch(sid(),new CommerceLifecycle.Dispatch("TEST",null,null),shop); lifecycle.simulate(sid(),true,buyer); }
    void approve(String id) { manager.decide(id,new ManagerReturnController.Decision(ReturnRequestStatus.APPROVED,"Valid evidence: approved physical return for QA."),shop); }
    void returned(String id) { approve(id); lifecycle.sendReturn(id,new CommerceLifecycle.Dispatch("TEST",null,null),buyer); lifecycle.simulateReturn(id,"picked",buyer); lifecycle.simulateReturn(id,"delivered",buyer); lifecycle.receiveReturn(id,shop); }
    @Test void paidOrderDispatchDeliveryReceiptAndFourteenDayWindowAreIdempotent() {
        lifecycle.confirm(sid(),shop); lifecycle.confirm(sid(),shop);
        var first=lifecycle.dispatch(sid(),new CommerceLifecycle.Dispatch("TEST",null,null),shop);
        assertEquals(first.get("shipmentId"),lifecycle.dispatch(sid(),new CommerceLifecycle.Dispatch("TEST",null,null),shop).get("shipmentId"));
        assertEquals(1,shipments.count());assertEquals("TEST",first.get("environment"));
        lifecycle.simulate(sid(),false,buyer); lifecycle.simulate(sid(),true,buyer);
        var saved=sellers.findById(sid()).orElseThrow();var deadline=saved.getReturnDeadline();
        assertEquals(saved.getDeliveredAt().plusDays(14),deadline);
        lifecycle.simulate(sid(),true,shop); lifecycle.receipt(order.getOrderId(),buyer); lifecycle.receipt(order.getOrderId(),buyer);
        assertEquals(deadline,sellers.findById(sid()).orElseThrow().getReturnDeadline());
        assertEquals(OrderStatus.COMPLETED,orders.findById(order.getOrderId()).orElseThrow().getStatus());
        assertNotNull(create(1,"after-receipt"));
    }
    @Test void unpaidForeignAndOutOfOrderCommandsCannotChangeFulfillment() {
        order.setStatus(OrderStatus.AWAITING_PAYMENT);orders.saveAndFlush(order);
        assertThrows(ResponseStatusException.class,()->lifecycle.dispatch(sid(),new CommerceLifecycle.Dispatch("TEST",null,null),shop));
        assertThrows(ResponseStatusException.class,()->lifecycle.confirm(sid(),buyer));
        assertThrows(ResponseStatusException.class,()->lifecycle.receipt(order.getOrderId(),buyer));
        assertThrows(ResponseStatusException.class,()->create(1,"early"));assertEquals(0,shipments.count());
    }
    @Test void liveManualWaybillCannotUseLocalSimulation() {
        lifecycle.dispatch(sid(),new CommerceLifecycle.Dispatch("MANUAL","carrier","existing-waybill"),shop);
        assertThrows(ResponseStatusException.class,()->lifecycle.simulate(sid(),true,buyer));
        assertThrows(ResponseStatusException.class,()->lifecycle.track(sid(),other));
        lifecycle.delivered(sid(),shop);assertEquals(SellerOrderStatus.DELIVERED,sellers.findById(sid()).orElseThrow().getStatus());
    }
    @Test void approvalDoesNotAllowRefundBeforeGoodsReachShopAndOnlyShopReceives() {
        delivered();String id=create(1,"physical");approve(id);
        assertThrows(ResponseStatusException.class,()->finance.refundBasis(id,internal));
        assertThrows(ResponseStatusException.class,()->lifecycle.getReturn(id,other));
        lifecycle.sendReturn(id,new CommerceLifecycle.Dispatch("TEST",null,null),buyer);
        assertThrows(ResponseStatusException.class,()->lifecycle.receiveReturn(id,shop));
        assertThrows(ResponseStatusException.class,()->lifecycle.simulateReturn(id,"delivered",buyer));
        lifecycle.simulateReturn(id,"picked",buyer);lifecycle.simulateReturn(id,"delivered",buyer);
        assertThrows(ResponseStatusException.class,()->lifecycle.receiveReturn(id,buyer));
        lifecycle.receiveReturn(id,shop);lifecycle.receiveReturn(id,shop);
        long amount=((Number)finance.refundBasis(id,internal).result().get("amountVnd")).longValue();assertEquals(33,amount);
        assertEquals(ReturnRequestStatus.REFUND_PENDING,returns.findById(id).orElseThrow().getStatus());
        assertNull(returns.findById(id).orElseThrow().getRefundReference());
        assertThrows(ResponseStatusException.class,()->finance.refunded(id,new RefundInput("tx",34L),internal));
        finance.refunded(id,new RefundInput("tx",amount),internal);finance.refunded(id,new RefundInput("tx",amount),internal);
        assertEquals(ReturnRequestStatus.REFUNDED,returns.findById(id).orElseThrow().getStatus());
    }
    @Test void partialRefundsAllocateRoundingExactlyOnceAndCannotExceedPaidValue() {
        delivered();long total=0;
        for(int i=0;i<3;i++) { String id=create(1,"partial-"+i);returned(id);long amount=((Number)finance.refundBasis(id,internal).result().get("amountVnd")).longValue();total+=amount;finance.refunded(id,new RefundInput("tx-"+i,amount),internal); }
        assertEquals(100,total);assertThrows(ResponseStatusException.class,()->create(1,"excess"));
    }
    @Test void requestRetriesCancellationRejectionAndExpiryRespectRemainingQuantity() {
        delivered();String id=create(2,"same");assertEquals(id,create(2,"same"));
        assertThrows(ResponseStatusException.class,()->create(1,"same"));
        assertThrows(ResponseStatusException.class,()->lifecycle.cancelReturn(id,other));
        lifecycle.cancelReturn(id,buyer);lifecycle.cancelReturn(id,buyer);
        String rejected=create(3,"reject");manager.decide(rejected,new ManagerReturnController.Decision(ReturnRequestStatus.REJECTED,"Insufficient evidence to approve this QA request."),shop);
        assertThrows(ResponseStatusException.class,()->finance.refundBasis(rejected,internal));
        assertNotNull(create(3,"remaining"));
        var saved=sellers.findById(sid()).orElseThrow();saved.setReturnDeadline(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));sellers.saveAndFlush(saved);
        assertThrows(ResponseStatusException.class,()->create(1,"expired"));
    }
    @Test void concurrentCustomerRequestsCannotReserveMoreThanPurchased() throws Exception {
        delivered();try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);var jobs=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++){final int n=i;jobs.add(pool.submit(()->{start.await();try{create(2,"race-"+n);return true;}catch(ResponseStatusException ex){return false;}}));}
            start.countDown();int success=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(1,returns.count());
        }
    }
    @Test void activeReturnsBlockSellerSettlement() {
        delivered();String id=create(1,"settlement");
        var saved=sellers.findById(sid()).orElseThrow(); saved.setStatus(SellerOrderStatus.COMPLETED);saved.setDeliveredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(16));saved.setReturnDeadline(LocalDateTime.now(ZoneOffset.UTC).minusDays(2));sellers.saveAndFlush(saved);
        assertThrows(ResponseStatusException.class,()->finance.settlementBasis(sid(),internal));assertNull(returns.findById(id).orElseThrow().getRefundReference());
    }
    @Test void simulationRequiresAnExplicitLocalFlag() {
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycle,"simulationEnabled",false);
        try { assertThrows(ResponseStatusException.class,()->lifecycle.dispatch(sid(),new CommerceLifecycle.Dispatch("TEST",null,null),shop)); assertEquals(0,shipments.count()); }
        finally { org.springframework.test.util.ReflectionTestUtils.setField(lifecycle,"simulationEnabled",true); }
    }
}
