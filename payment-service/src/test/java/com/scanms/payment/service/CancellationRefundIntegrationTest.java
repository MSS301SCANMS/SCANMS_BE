package com.scanms.payment.service;

import com.scanms.payment.client.CancellationOrderClient;
import com.scanms.payment.constant.*;
import com.scanms.payment.entity.Payment;
import com.scanms.payment.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:cancel-refunds;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.payment.service-client-secret=", "logging.level.root=WARN"})
class CancellationRefundIntegrationTest {
    @Autowired
    CancellationRefundService service;
    @Autowired
    PaymentRepository payments;
    @Autowired
    WalletRepository wallets;
    @Autowired
    WalletTransactionRepository entries;
    @Autowired
    FinanceEventRepository events;
    @MockitoBean
    CancellationOrderClient orders;
    @MockitoBean
    PaymentAccess access;
    @MockitoBean
    JwtDecoder decoder;

    @BeforeEach
    void setup() {
        events.deleteAll();
        payments.deleteAll();
        entries.deleteAll();
        wallets.deleteAll();
        when(access.role("ORDER_INTERNAL")).thenReturn(true);
        when(access.token()).thenReturn("order-token");
        when(orders.basis("order", "Bearer order-token")).thenReturn(Map.of("result", Map.of("orderId", "order", "customerId", "buyer", "amountVnd", 200L, "currency", "VND")));
    }

    private Payment captured() {
        return payments.saveAndFlush(Payment.builder().orderId("order").activeOrderRef("order").payerId("buyer").purpose("ORDER")
                .providerCode("WALLET").providerOrderCode(123L).idempotencyKey("paid").amountVnd(200L).currency("VND")
                .status(PaymentStatus.SUCCESS).verifiedAt(LocalDateTime.now()).build());
    }

    @Test
    void cancellationRefundCreatesOneCreditAndOneDurableAcknowledgementEvenUnderConcurrentRetries() throws Exception {
        var payment = captured();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var attempts = new ArrayList<Future<Map<String, Object>>>();
            for (int index = 0; index < 2; index++)
                attempts.add(executor.submit(() -> {
                    start.await();
                    return service.refund("order");
                }));
            start.countDown();
            var first = attempts.getFirst().get(20, TimeUnit.SECONDS);
            assertEquals(first.get("refundReference"), attempts.getLast().get(20, TimeUnit.SECONDS).get("refundReference"));
        }
        assertEquals(1, entries.count());
        assertEquals(200L, wallets.findAll().getFirst().getAvailableBalanceVnd());
        assertEquals(1, events.count());
        assertEquals("REFUNDED", payments.findById(payment.getPaymentId()).orElseThrow().getOrderSyncStatus());
    }

    @Test
    void aLateCaptureCanBeRecoveredButForgedAmountsNeverCreditMoney() {
        assertEquals("NO_CAPTURED_PAYMENT", service.refund("order").get("refundStatus"));
        captured();
        when(orders.basis("order", "Bearer order-token")).thenReturn(Map.of("result", Map.of("orderId", "order", "customerId", "buyer", "amountVnd", 201L, "currency", "VND")));
        assertThrows(RuntimeException.class, () -> service.refund("order"));
        assertEquals(0, entries.count());
        when(orders.basis("order", "Bearer order-token")).thenReturn(Map.of("result", Map.of("orderId", "order", "customerId", "buyer", "amountVnd", 200L, "currency", "VND")));
        assertEquals("REFUNDED", service.refund("order").get("refundStatus"));
        assertEquals(1, entries.count());
    }
}
