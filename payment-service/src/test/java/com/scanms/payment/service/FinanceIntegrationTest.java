package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.MoneyDtos.*;
import com.scanms.payment.dto.request.*;
import com.scanms.payment.entity.*;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:finance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.payment.bank-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF", "debug=false"
})
class FinanceIntegrationTest {
    @Autowired javax.sql.DataSource migrationDataSource;
    @Test void postgresMigrationsCanBeAppliedTwice() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SCANMS_TEST_POSTGRES_PREFIX")!=null);
        try(var connection=migrationDataSource.getConnection();var statement=connection.createStatement()) {
            for(int pass=0;pass<2;pass++) {
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","finance-upgrade.sql")));
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","finance-reconciliation-upgrade.sql")));
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","bank-calendar-upgrade.sql")));
                statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("db","payment-bank-details-upgrade.sql")));
            }
        }
    }

    // Optional real PostgreSQL run; only explicitly named QA databases may be dropped by Hibernate.
    @org.springframework.test.context.DynamicPropertySource
    static void postgresDatabase(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix=System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if(prefix==null || prefix.isBlank()) return;
        if(!prefix.startsWith("jdbc:postgresql://") || !prefix.endsWith("/scanms_qa_")) throw new IllegalArgumentException("Use isolated scanms_qa_ databases only");
        r.add("spring.datasource.url",() -> prefix+"payment");
        r.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        r.add("spring.datasource.username",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER","scanms_qa"));
        r.add("spring.datasource.password",() -> System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD",""));
    }

    @Autowired FinanceService finance;
    @Autowired WalletTransactionService ledger;
    @Autowired PaymentWorkflow payments;
    @Autowired WithdrawalProcessor processor;
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository entries;
    @Autowired BankAccountRepository banks;
    @Autowired WithdrawalRequestRepository withdrawals;
    @Autowired SellerSettlementRepository settlements;
    @Autowired PaymentRepository paymentRows;
    @Autowired FeeConfigRepository fees;
    @Autowired FinanceEventRepository events;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean FinanceOutbox outbox;
    @MockitoBean BankBusinessCalendar calendar;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @MockitoBean PaymentAccess access;
    @MockitoBean PayosProvider provider;
    @MockitoBean JwtDecoder decoder;
    @Test void httpRejectsUnauthenticatedForeignWalletAndLegacyWrite() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/finance/withdrawals"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token").header("alg","RS256").subject("identity")
                .claim("realm_access",Map.of("roles",List.of("USER"))).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(decoder.decode("test-token")).thenReturn(jwt);
        Wallet w=wallet(WalletOwnerType.STORE,0);
        doThrow(new AppException(com.scanms.payment.exception.ErrorCode.FORBIDDEN)).when(access).wallet(any());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/finance/wallets/"+w.getWalletId()).header("Authorization","Bearer test-token"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/payments").header("Authorization","Bearer test-token").contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @BeforeEach void setup() {
        events.deleteAll(); entries.deleteAll(); withdrawals.deleteAll(); settlements.deleteAll(); paymentRows.deleteAll(); banks.deleteAll(); wallets.deleteAll(); fees.deleteAll();
        when(access.operator()).thenReturn(true); when(access.role("ADMIN")).thenReturn(true); when(access.userId()).thenReturn("customer");
        when(provider.payoutAvailable()).thenReturn(true);
        when(provider.available()).thenReturn(true);
        when(calendar.nextProcessingAt(any())).thenAnswer(call -> call.getArgument(0));
    }
    Wallet wallet(WalletOwnerType type, long balance) {
        Wallet w=finance.ensureWallet(type, type==WalletOwnerType.CUSTOMER?"customer":"owner");
        w.setAvailableBalanceVnd(balance); return wallets.saveAndFlush(w);
    }
    CreateWalletTransactionRequest command(String wallet,String key,long amount,WalletTransactionType type,WalletTransactionDirection direction) {
        return new CreateWalletTransactionRequest(wallet,type,direction,amount,WalletTransactionStatus.SUCCESS,"TEST","reference",key,"Test",null);
    }
    WithdrawalView request(Wallet w) {
        BankView bank=finance.registerBank(new BankInput(w.getOwnerType(),w.getOwnerRefId(),"970422","NGUYEN VAN A","123456789"));
        finance.bankStatus(bank.bankAccountId(),BankAccountVerificationStatus.VERIFIED);
        return finance.requestWithdrawal(new WithdrawalInput(w.getWalletId(),bank.bankAccountId(),200_000L,UUID.randomUUID().toString()));
    }
    void due(String id) { WithdrawalRequest w=withdrawals.findById(id).orElseThrow(); w.setScheduledFor(Instant.now().minusSeconds(1)); withdrawals.saveAndFlush(w); }
    Map<String,Object> payout(String id,String state,long amount) {
        return Map.of("id","provider-payout", "referenceId",id,"transactions",List.of(Map.of("amount",amount,"toBin","970422","toAccountNumber","123456789","state",state)));
    }
    @Test void parallelDuplicateCreditPostsExactlyOnce() throws Exception {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,0); var r=command(w.getWalletId(),"same-command",100_000,WalletTransactionType.TOP_UP,WalletTransactionDirection.CREDIT);
        try(var executor=Executors.newFixedThreadPool(8)) {
            CountDownLatch start=new CountDownLatch(1); List<Future<String>> results=new ArrayList<>();
            for(int i=0;i<8;i++) results.add(executor.submit(() -> { start.await(); return ledger.create(r).transactionId(); }));
            start.countDown(); Set<String> ids=new HashSet<>(); for(var result:results) ids.add(result.get(20,TimeUnit.SECONDS)); assertEquals(1,ids.size());
        }
        assertEquals(100_000,wallets.findById(w.getWalletId()).orElseThrow().getAvailableBalanceVnd()); assertEquals(1,entries.count());
    }
    @Test void changedIdempotencyPayloadIsRejected() {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,0); ledger.create(command(w.getWalletId(),"key",10,WalletTransactionType.TOP_UP,WalletTransactionDirection.CREDIT));
        assertThrows(AppException.class,() -> ledger.create(command(w.getWalletId(),"key",20,WalletTransactionType.TOP_UP,WalletTransactionDirection.CREDIT)));
        assertEquals(10,wallets.findById(w.getWalletId()).orElseThrow().getAvailableBalanceVnd());
    }
    @Test void enclosingTransactionFailureRollsBackBalanceAndLedger() {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,100);
        assertThrows(IllegalStateException.class,() -> new TransactionTemplate(manager).executeWithoutResult(s -> {
            ledger.create(command(w.getWalletId(),"rollback",40,WalletTransactionType.ORDER_PAYMENT,WalletTransactionDirection.DEBIT)); throw new IllegalStateException("Downstream local failure");
        }));
        assertEquals(100,wallets.findById(w.getWalletId()).orElseThrow().getAvailableBalanceVnd()); assertEquals(0,entries.count());
    }
    @Test void approvalHoldsOnceAndRejectionReleasesOnce() {
        Wallet w=wallet(WalletOwnerType.COLLABORATOR,500_000); var r=request(w);
        assertEquals(500_000,wallets.findById(w.getWalletId()).orElseThrow().getAvailableBalanceVnd());
        finance.approveWithdrawal(r.withdrawalId()); finance.approveWithdrawal(r.withdrawalId());
        Wallet held=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(300_000,held.getAvailableBalanceVnd()); assertEquals(200_000,held.getHeldBalanceVnd());
        finance.rejectWithdrawal(r.withdrawalId(),"Rejected",false); finance.rejectWithdrawal(r.withdrawalId(),"Rejected",false);
        Wallet released=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(500_000,released.getAvailableBalanceVnd()); assertEquals(0,released.getHeldBalanceVnd()); assertEquals(2,entries.count());
    }
    @Test void competingApprovalsCannotOverdraw() throws Exception {
        Wallet w=wallet(WalletOwnerType.STORE,300_000); var a=request(w); var b=request(w);
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1); List<Future<Boolean>> results=new ArrayList<>();
            for(var r:List.of(a,b)) results.add(executor.submit(() -> { start.await(); try { finance.approveWithdrawal(r.withdrawalId()); return true; } catch(AppException e) { return false; } }));
            start.countDown(); int success=0; for(var result:results) if(result.get(20,TimeUnit.SECONDS)) success++; assertEquals(1,success);
        }
        Wallet held=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(100_000,held.getAvailableBalanceVnd()); assertEquals(200_000,held.getHeldBalanceVnd());
    }
    @Test void unknownTransferRetainsHoldAndRetryReconcilesWithoutSendingTwice() {
        Wallet w=wallet(WalletOwnerType.STORE,500_000); var r=request(w); finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(provider.findPayout(r.withdrawalId())).thenReturn(Map.of());
        when(provider.transfer(anyString(),anyLong(),anyString(),anyString())).thenThrow(new IllegalStateException("Response lost"));
        assertEquals(WithdrawalStatus.PROCESSING,processor.execute(r.withdrawalId()).status());
        assertEquals(200_000,wallets.findById(w.getWalletId()).orElseThrow().getHeldBalanceVnd());
        when(provider.findPayout(r.withdrawalId())).thenReturn(payout(r.withdrawalId(),"SUCCEEDED",200_000));
        assertEquals(WithdrawalStatus.SUCCESS,processor.reconcile(r.withdrawalId()).status()); processor.reconcile(r.withdrawalId());
        verify(provider,times(1)).transfer(anyString(),anyLong(),anyString(),anyString());
        Wallet paid=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(300_000,paid.getAvailableBalanceVnd()); assertEquals(0,paid.getHeldBalanceVnd()); assertEquals(2,entries.count());
    }
    @Test void verifiedTransferFailureReleasesHold() {
        Wallet w=wallet(WalletOwnerType.COLLABORATOR,500_000); var r=request(w); finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(provider.findPayout(r.withdrawalId())).thenReturn(payout(r.withdrawalId(),"FAILED",200_000));
        assertEquals(WithdrawalStatus.FAILED,processor.execute(r.withdrawalId()).status());
        Wallet released=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(500_000,released.getAvailableBalanceVnd()); assertEquals(0,released.getHeldBalanceVnd());
    }
    @Test void payoutNotificationRequeriesProviderAndDuplicateCannotDebitTwice() {
        Wallet w=wallet(WalletOwnerType.STORE,500_000); var r=request(w); finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(provider.findPayout(r.withdrawalId())).thenReturn(payout(r.withdrawalId(),"PROCESSING",200_000));
        processor.execute(r.withdrawalId());
        var row=withdrawals.findById(r.withdrawalId()).orElseThrow();
        Map<String,Object> callback=Map.of("id",row.getProviderReference(),"referenceId",r.withdrawalId(),"state","FAILED");
        when(provider.payoutStatus(row.getProviderReference())).thenReturn(payout(r.withdrawalId(),"SUCCEEDED",200_000));
        assertEquals(WithdrawalStatus.SUCCESS,processor.notification(callback,"signed").status());
        processor.notification(callback,"signed");
        assertEquals(2,entries.count()); assertEquals(0,wallets.findById(w.getWalletId()).orElseThrow().getHeldBalanceVnd());
        verify(provider,times(1)).payoutStatus(row.getProviderReference());
        verify(provider,never()).transfer(anyString(),anyLong(),anyString(),anyString());
    }
    @Test void payoutNotificationWithWrongIdentityOrSignatureCannotChangeMoney() {
        Wallet w=wallet(WalletOwnerType.STORE,500_000); var r=request(w); finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(provider.findPayout(r.withdrawalId())).thenReturn(payout(r.withdrawalId(),"PROCESSING",200_000)); processor.execute(r.withdrawalId());
        assertThrows(AppException.class,()->processor.notification(Map.of("id","wrong","referenceId",r.withdrawalId()),"signed"));
        doThrow(new AppException(com.scanms.payment.exception.ErrorCode.INVALID_REQUEST)).when(provider).verifyPayoutNotification(anyMap(),eq("bad"));
        assertThrows(AppException.class,()->processor.notification(Map.of("referenceId",r.withdrawalId()),"bad"));
        assertEquals(1,entries.count()); assertEquals(200_000,wallets.findById(w.getWalletId()).orElseThrow().getHeldBalanceVnd());
        verify(provider,never()).payoutStatus(anyString());
    }
    @Test void wrongProviderDestinationNeverDebitsOrReleases() {
        Wallet w=wallet(WalletOwnerType.STORE,500_000); var r=request(w); finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(provider.findPayout(r.withdrawalId())).thenReturn(Map.of("id","provider", "referenceId",r.withdrawalId(),"transactions",List.of(Map.of("amount",200_000,"toBin","970422","toAccountNumber","wrong","state","SUCCEEDED"))));
        assertEquals(WithdrawalStatus.PROCESSING,processor.execute(r.withdrawalId()).status()); assertEquals(200_000,wallets.findById(w.getWalletId()).orElseThrow().getHeldBalanceVnd()); assertEquals(1,entries.count());
    }
    @Test void refundUsesTrustedAmountAndRetriesOnce() {
        when(access.get(contains("refund-basis"))).thenReturn(Map.of("customerId","customer","amountVnd",12345,"currency","VND","returnRequestId","return","orderItemId","line"));
        var a=finance.refund("return"); var b=finance.refund("return"); assertEquals(a.transactionId(),b.transactionId()); assertEquals(1,entries.count()); assertEquals(1,events.count());
        assertEquals(12345,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.CUSTOMER,"customer","VND").orElseThrow().getAvailableBalanceVnd());
    }
    @Test void settlementKeepsFeeSnapshotAndCreditsStoreAndPlatformOnce() {
        when(access.get(contains("settlement-basis"))).thenReturn(Map.of("storeId","owner","orderItemIds","line","grossRevenueVnd",1_000_000,"refundVnd",100_000,"sellerDiscountVnd",50_000,"platformSubsidyVnd",20_000,"feeBasisVnd",850_000));
        when(access.get(contains("commissions-total"))).thenReturn(Map.of("amountVnd",30_000,"finalized",true));
        var f=finance.createFee(new CreateFeeConfigRequest(FeeType.PLATFORM_FEE,FeeCalculationType.PERCENTAGE,new BigDecimal("2.5"),null,null,null,Instant.now().minusSeconds(60),null,FeeConfigStatus.ACTIVE));
        var s=finance.computeSettlement("seller"); assertEquals(818_750,s.netAmountVnd());
        finance.feeStatus(f.feeConfigId(),FeeConfigStatus.INACTIVE);
        finance.creditSettlement(s.settlementId()); finance.creditSettlement(s.settlementId());
        assertEquals(818_750,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.STORE,"owner","VND").orElseThrow().getAvailableBalanceVnd());
        assertEquals(21_250,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.PLATFORM,"SCANMS","VND").orElseThrow().getAvailableBalanceVnd()); assertEquals(2,entries.count());
    }
    @Test void providerReceivingAccountSurvivesCreateResumeAndStatusRefresh() {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,0);
        when(provider.createLink(any())).thenAnswer(call -> {
            Payment p=call.getArgument(0);
            return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),
                    "checkoutUrl","https://pay.payos.vn/web/test","qrCode","qr","status","PENDING",
                    "bin","970418","accountNumber","V3CAS-TEST","accountName","TEST OWNER","description","TEST ORDER");
        });
        var created=payments.topup(new TopUpInput(w.getWalletId(),50_000L,"bank-details-key"));
        Payment stored=paymentRows.findById(created.paymentId()).orElseThrow();
        assertEquals("970418",stored.getBin());
        assertEquals("V3CAS-TEST",stored.getAccountNumber());
        assertEquals("TEST OWNER",stored.getAccountName());
        assertEquals("TEST ORDER",stored.getDescription());
        when(provider.paymentStatus(stored.getProviderOrderCode())).thenReturn(Map.of(
                "orderCode",stored.getProviderOrderCode(),"amount",stored.getAmountVnd(),"status","PENDING"));
        var refreshed=payments.status(created.paymentId(),true);
        assertEquals(created.bin(),refreshed.bin());
        assertEquals(created.accountNumber(),refreshed.accountNumber());
        assertEquals(created.accountName(),refreshed.accountName());
        assertEquals(created.description(),refreshed.description());
        assertEquals(created.accountNumber(),payments.resume(created.paymentId()).accountNumber());
        verify(provider,times(1)).createLink(any());
    }
    @Test void freshPaymentCreatesLinkAndDuplicateWebhookCreditsOnlyOnce() {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,0);
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","qrCode","qr","status","PENDING"); });
        var p=payments.topup(new TopUpInput(w.getWalletId(),50_000L,"topup-key"));
        assertNotNull(p.checkoutUrl()); verify(provider,never()).paymentStatus(anyLong());
        when(provider.paymentStatus(p.providerOrderCode())).thenReturn(Map.of("orderCode",p.providerOrderCode(),"amount",50_000,"amountPaid",50_000,"status","PAID","id","provider-link"));
        Map<String,Object> event=Map.of("data",Map.of("code","00","orderCode",p.providerOrderCode(),"amount",50_000,"currency","VND","reference","bank-ref"),"signature","verified-by-provider-mock");
        payments.webhook(event); payments.webhook(event);
        assertEquals(50_000,wallets.findById(w.getWalletId()).orElseThrow().getAvailableBalanceVnd()); assertEquals(1,entries.count());
    }
    @Test void verifiedSurplusRefundsOnceAndUnappliedRefundNeverReturnsItTwice() {
        when(access.get(contains("/orders/"))).thenReturn(Map.of("customerId","customer","payableVnd",50_000,"status","PENDING","currency","VND"));
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","status","PENDING"); });
        var p=payments.create(new PaymentInput("order","surplus-order"));
        when(provider.paymentStatus(p.providerOrderCode())).thenReturn(Map.of("orderCode",p.providerOrderCode(),"amount",50_000,"amountPaid",60_000,"status","PAID","id","verified-link"));
        var paid=payments.status(p.paymentId(),true); assertEquals(60_000,paid.receivedAmountVnd());
        var refund=payments.refundSurplus(p.paymentId(),"Verified excess transfer");
        assertEquals(10_000,refund.surplusRefundedAmountVnd()); assertEquals(refund.surplusReference(),payments.refundSurplus(p.paymentId(),"Retry").surplusReference());
        var row=paymentRows.findById(p.paymentId()).orElseThrow(); row.setOrderSyncStatus("RECONCILIATION_REQUIRED"); paymentRows.saveAndFlush(row);
        when(access.get(contains("unapplied-payment"))).thenReturn(Map.of("refundable",true,"customerId","customer","currency","VND","amountVnd",50_000));
        var late=payments.refundUnapplied(p.paymentId(),"Order expired"); assertEquals(50_000,late.resolutionAmountVnd());
        payments.refundUnapplied(p.paymentId(),"Retry"); payments.refundSurplus(p.paymentId(),"Retry");
        assertEquals(60_000,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.CUSTOMER,"customer","VND").orElseThrow().getAvailableBalanceVnd()); assertEquals(2,entries.count());
    }
    @Test void partialTransferCannotConfirmOrderButVerifiedAccumulatedTransfersCan() {
        when(access.get(contains("/orders/"))).thenReturn(Map.of("customerId","customer","payableVnd",50_000,"status","PENDING","currency","VND"));
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","status","PENDING"); });
        var p=payments.create(new PaymentInput("order","partial-order"));
        Map<String,Object> event=Map.of("data",Map.of("code","00","orderCode",p.providerOrderCode(),"amount",30_000,"currency","VND","reference","partial-transfer"),"signature","verified");
        when(provider.paymentStatus(p.providerOrderCode())).thenReturn(Map.of("orderCode",p.providerOrderCode(),"amount",50_000,"amountPaid",30_000,"status","PENDING"));
        payments.webhook(event); assertNotEquals(PaymentStatus.SUCCESS,payments.status(p.paymentId(),false).status()); assertEquals(0,events.count());
        when(provider.paymentStatus(p.providerOrderCode())).thenReturn(Map.of("orderCode",p.providerOrderCode(),"amount",50_000,"amountPaid",60_000,"status","PAID","id","verified-link"));
        payments.webhook(event); payments.webhook(event); assertEquals(PaymentStatus.SUCCESS,payments.status(p.paymentId(),false).status()); assertEquals(1,events.count());
        when(provider.paymentStatus(p.providerOrderCode())).thenReturn(Map.of("orderCode",p.providerOrderCode(),"amount",50_000,"amountPaid",20_000,"status","PAID"));
        assertThrows(AppException.class,() -> payments.status(p.paymentId(),true)); assertEquals(60_000,payments.status(p.paymentId(),false).receivedAmountVnd());
    }
    @Test void duplicateActiveOrderIsRejectedByDatabaseConstraint() {
        when(access.get(contains("/orders/"))).thenReturn(Map.of("customerId","customer","payableVnd",50_000,"status","PENDING","currency","VND"));
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","status","PENDING"); });
        payments.create(new PaymentInput("order","attempt-one")); assertThrows(AppException.class,() -> payments.create(new PaymentInput("order","attempt-two")));
        assertEquals(1,paymentRows.count());
    }
    @Test void frozenWalletAcceptsRefundAndReleaseButPreventsNewHold() {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,500);
        ledger.create(command(w.getWalletId(),"initial-hold",200,WalletTransactionType.HOLD,WalletTransactionDirection.DEBIT));
        finance.walletStatus(w.getWalletId(),WalletStatus.FROZEN);
        assertThrows(AppException.class,() -> ledger.create(command(w.getWalletId(),"new-hold",100,WalletTransactionType.HOLD,WalletTransactionDirection.DEBIT)));
        ledger.create(command(w.getWalletId(),"refund",50,WalletTransactionType.REFUND,WalletTransactionDirection.CREDIT));
        ledger.create(command(w.getWalletId(),"release",200,WalletTransactionType.RELEASE,WalletTransactionDirection.CREDIT));
        Wallet actual=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(550,actual.getAvailableBalanceVnd()); assertEquals(0,actual.getHeldBalanceVnd());
        assertThrows(AppException.class,() -> finance.walletStatus(w.getWalletId(),WalletStatus.CLOSED));
    }
    @Test void commissionCreditsOnlyOnceAndCannotReuseReferenceForChangedAmount() {
        when(access.get(contains("commission-basis"))).thenReturn(Map.of("collaboratorId","owner","amountVnd",15_000));
        var first=finance.commission("commission"); assertEquals(first.transactionId(),finance.commission("commission").transactionId());
        when(access.get(contains("commission-basis"))).thenReturn(Map.of("collaboratorId","owner","amountVnd",20_000));
        assertThrows(AppException.class,() -> finance.commission("commission"));
        assertEquals(15_000,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.COLLABORATOR,"owner","VND").orElseThrow().getAvailableBalanceVnd()); assertEquals(1,events.count());
    }
    @Test void withdrawalFeeIsSnapshottedAndCreditedToPlatformOnVerifiedSuccess() {
        finance.createFee(new CreateFeeConfigRequest(FeeType.WITHDRAWAL_FEE,FeeCalculationType.FIXED,null,5000L,null,null,Instant.now().minusSeconds(60),null,FeeConfigStatus.ACTIVE));
        Wallet w=wallet(WalletOwnerType.STORE,500_000); var r=request(w); assertEquals(195_000,r.netAmountVnd());
        finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(provider.findPayout(r.withdrawalId())).thenReturn(payout(r.withdrawalId(),"SUCCEEDED",195_000));
        assertEquals(WithdrawalStatus.SUCCESS,processor.execute(r.withdrawalId()).status());
        assertEquals(5000,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.PLATFORM,"SCANMS","VND").orElseThrow().getAvailableBalanceVnd());
        Wallet actual=wallets.findById(w.getWalletId()).orElseThrow(); assertEquals(300_000,actual.getAvailableBalanceVnd()); assertEquals(0,actual.getHeldBalanceVnd());
    }
    @Test void percentagePrecisionSurvivesDatabaseRoundTrip() {
        var row=finance.createFee(new CreateFeeConfigRequest(FeeType.SERVICE_FEE,FeeCalculationType.PERCENTAGE,new BigDecimal("2.555555"),null,null,null,Instant.now(),null,FeeConfigStatus.DRAFT));
        assertEquals(0,new BigDecimal("2.555555").compareTo(fees.findById(row.feeConfigId()).orElseThrow().getRatePercent()));
    }
    @Test void pendingTopupPreventsClosingEmptyWalletAndUnavailableProviderDoesNotAllocate() {
        Wallet w=wallet(WalletOwnerType.CUSTOMER,0);
        when(provider.available()).thenReturn(false);
        assertThrows(AppException.class,() -> payments.topup(new TopUpInput(w.getWalletId(),50_000L,"unavailable"))); assertEquals(0,paymentRows.count());
        when(provider.available()).thenReturn(true);
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","status","PENDING"); });
        payments.topup(new TopUpInput(w.getWalletId(),50_000L,"pending"));
        assertThrows(AppException.class,() -> finance.walletStatus(w.getWalletId(),WalletStatus.CLOSED));
    }
    @Test void refundRejectsWrongCurrencyAndReferenceBeforeAnyCredit() {
        when(access.get(contains("refund-basis"))).thenReturn(Map.of("customerId","customer","amountVnd",100,"currency","USD","returnRequestId","return","orderItemId","line"));
        assertThrows(AppException.class,() -> finance.refund("return")); assertEquals(0,entries.count());
        when(access.get(contains("refund-basis"))).thenReturn(Map.of("customerId","customer","amountVnd",100,"currency","VND","returnRequestId","different","orderItemId","line"));
        assertThrows(AppException.class,() -> finance.refund("return")); assertEquals(0,entries.count());
    }
    @Test void refundRetryReturnsCommittedResultEvenWhenSourceIsUnavailable() {
        when(access.get(contains("refund-basis"))).thenReturn(Map.of("customerId","customer","amountVnd",100,"currency","VND","returnRequestId","return","orderItemId","line"));
        var first=finance.refund("return"); when(access.get(contains("refund-basis"))).thenThrow(new IllegalStateException("Source offline"));
        assertEquals(first.transactionId(),finance.refund("return").transactionId()); assertEquals(1,entries.count());
    }
    @Test void paymentKeyCannotCrossPaymentMethods() {
        Wallet wallet=wallet(WalletOwnerType.CUSTOMER,100_000);
        when(access.get(contains("/orders/"))).thenReturn(Map.of("customerId","customer","payableVnd",50_000,"status","PENDING","currency","VND"));
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","status","PENDING"); });
        payments.create(new PaymentInput("order","payos-key"));
        assertThrows(AppException.class,() -> payments.payWallet(new WalletPayInput("order",wallet.getWalletId(),"payos-key")));
        assertEquals(100_000,wallets.findById(wallet.getWalletId()).orElseThrow().getAvailableBalanceVnd()); assertEquals(0,entries.count());
    }
    @Test void activatedFeeCannotReturnToDraftThroughInactiveState() {
        var fee=finance.createFee(new CreateFeeConfigRequest(FeeType.SERVICE_FEE,FeeCalculationType.FIXED,null,100L,null,null,Instant.now(),null,FeeConfigStatus.ACTIVE));
        assertThrows(AppException.class,() -> finance.feeStatus(fee.feeConfigId(),FeeConfigStatus.DRAFT));
        finance.feeStatus(fee.feeConfigId(),FeeConfigStatus.INACTIVE);
        assertThrows(AppException.class,() -> finance.feeStatus(fee.feeConfigId(),FeeConfigStatus.DRAFT));
        assertNotNull(fees.findById(fee.feeConfigId()).orElseThrow().getActivatedAt());
    }
    @Test void delayedTransferReschedulesOnClosedDayButExistingPayoutIsStillReconciled() {
        Wallet wallet=wallet(WalletOwnerType.STORE,500_000); var r=request(wallet); finance.approveWithdrawal(r.withdrawalId()); due(r.withdrawalId());
        when(calendar.nextProcessingAt(any())).thenAnswer(call -> ((Instant)call.getArgument(0)).plusSeconds(86400));
        when(provider.findPayout(r.withdrawalId())).thenReturn(Map.of()); processor.execute(r.withdrawalId());
        verify(provider,never()).transfer(anyString(),anyLong(),anyString(),anyString()); assertEquals(200_000,wallets.findById(wallet.getWalletId()).orElseThrow().getHeldBalanceVnd());
        when(provider.findPayout(r.withdrawalId())).thenReturn(payout(r.withdrawalId(),"SUCCEEDED",200_000));
        assertEquals(WithdrawalStatus.SUCCESS,processor.execute(r.withdrawalId()).status());
    }
    @Test void settlementRejectsUnfinalizedCommissionEvenWhenAmountIsZero() {
        when(access.get(contains("settlement-basis"))).thenReturn(Map.of("storeId","owner","orderItemIds","line","grossRevenueVnd",100,"refundVnd",0,"sellerDiscountVnd",0,"platformSubsidyVnd",0,"feeBasisVnd",100));
        when(access.get(contains("commissions-total"))).thenReturn(Map.of("amountVnd",0));
        assertThrows(AppException.class,() -> finance.computeSettlement("seller")); assertEquals(0,settlements.count());
    }
    @Test void latePaymentStopsRetryingBusinessConflictAndRefundsCustomerOnlyOnce() throws Exception {
        when(access.get(contains("/orders/"))).thenReturn(Map.of("customerId","customer","payableVnd",50_000,"status","PENDING","currency","VND"));
        when(provider.createLink(any())).thenAnswer(call -> { Payment p=call.getArgument(0); return Map.of("orderCode",p.getProviderOrderCode(),"amount",p.getAmountVnd(),"checkoutUrl","https://pay.payos.vn/web/test","status","PENDING"); });
        var p=payments.create(new PaymentInput("order","late")); Payment row=paymentRows.findById(p.paymentId()).orElseThrow(); row.setStatus(PaymentStatus.CANCELLED); row.setActiveOrderRef(null); paymentRows.saveAndFlush(row);
        payments.webhook(Map.of("data",Map.of("code","00","orderCode",p.providerOrderCode(),"amount",50_000,"currency","VND","reference","late-bank-reference"),"signature","verified"));
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0); var calls=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/paid",exchange -> { calls.incrementAndGet(); byte[] body="Late payment requires reconciliation".getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(409,body.length); exchange.getResponseBody().write(body); exchange.close(); }); server.start();
        try {
            var event=events.findAll().getFirst(); event.setDestination("http://127.0.0.1:"+server.getAddress().getPort()+"/paid"); events.saveAndFlush(event);
            doReturn(Optional.of("service-token")).when(outbox).serviceToken(); outbox.dispatch(); outbox.dispatch();
            assertEquals(1,calls.get()); assertTrue(events.findById(event.getEventId()).orElseThrow().isBlocked());
            assertEquals("RECONCILIATION_REQUIRED",paymentRows.findById(p.paymentId()).orElseThrow().getOrderSyncStatus());
            when(access.get(contains("unapplied-payment"))).thenReturn(Map.of("refundable",true,"customerId","customer","currency","VND","amountVnd",50_000));
            var refund=payments.refundUnapplied(p.paymentId(),"Order expired before provider payment");
            assertEquals("REFUNDED",refund.orderSyncStatus()); assertEquals(refund.resolutionReference(),payments.refundUnapplied(p.paymentId(),"Retry").resolutionReference());
            assertEquals(50_000,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.CUSTOMER,"customer","VND").orElseThrow().getAvailableBalanceVnd()); assertEquals(1,entries.count());
        } finally { server.stop(0); }
    }
    @Test void bankReplacementKeepsExistingWithdrawalDestinationSnapshotAndRequiresVerification() {
        Wallet wallet=wallet(WalletOwnerType.STORE,500_000); var withdrawal=request(wallet);
        String before=withdrawals.findById(withdrawal.withdrawalId()).orElseThrow().getDestinationSnapshot();
        var input=new BankInput(WalletOwnerType.STORE,"owner","970422","NEW OWNER","987654321");
        var replacement=finance.replaceBank(withdrawal.bankAccountId(),input);
        assertEquals(BankAccountVerificationStatus.PENDING,replacement.verificationStatus()); assertFalse(banks.findById(withdrawal.bankAccountId()).orElseThrow().getActive());
        assertEquals(before,withdrawals.findById(withdrawal.withdrawalId()).orElseThrow().getDestinationSnapshot());
        assertEquals(replacement.bankAccountId(),finance.replaceBank(withdrawal.bankAccountId(),input).bankAccountId());
        assertThrows(AppException.class,() -> finance.requestWithdrawal(new WithdrawalInput(wallet.getWalletId(),replacement.bankAccountId(),200_000L,"unverified")));
    }
    @Test void importingKycDoesNotAllowCustomerToSelfVerifyBankOwnership() {
        var input=new BankInput(WalletOwnerType.COLLABORATOR,"owner","970422","OWNER","123456789");
        var first=finance.syncKycBank(new KycBankInput(input,"kyc-case",false));
        assertEquals(BankAccountVerificationStatus.PENDING,first.verificationStatus());
        assertEquals(first.bankAccountId(),finance.syncKycBank(new KycBankInput(input,"kyc-case",false)).bankAccountId());
        assertThrows(AppException.class,() -> finance.syncKycBank(new KycBankInput(input,"kyc-case",true)));
    }
    @Test void paidSettlementReconciliationUsesOriginalFeeRateAndPostsOnlyTheDifference() {
        when(access.get(contains("settlement-basis"))).thenReturn(Map.of("storeId","owner","orderItemIds","line","grossRevenueVnd",100_000,"refundVnd",0,"sellerDiscountVnd",0,"platformSubsidyVnd",0,"feeBasisVnd",100_000));
        when(access.get(contains("commissions-total"))).thenReturn(Map.of("amountVnd",0,"finalized",true));
        var fee=finance.createFee(new CreateFeeConfigRequest(FeeType.PLATFORM_FEE,FeeCalculationType.PERCENTAGE,new BigDecimal("2.5"),null,null,null,Instant.now().minusSeconds(60),null,FeeConfigStatus.ACTIVE));
        var settlement=finance.computeSettlement("seller"); finance.creditSettlement(settlement.settlementId()); finance.feeStatus(fee.feeConfigId(),FeeConfigStatus.INACTIVE);
        when(access.get(contains("settlement-basis"))).thenReturn(Map.of("storeId","owner","orderItemIds","line","grossRevenueVnd",100_000,"refundVnd",20_000,"sellerDiscountVnd",0,"platformSubsidyVnd",0,"feeBasisVnd",80_000));
        var updated=finance.reconcileSettlement(settlement.settlementId(),"Correct source refund snapshot"); assertEquals(78_000,updated.netAmountVnd());
        finance.reconcileSettlement(settlement.settlementId(),"Repeat same source state");
        assertEquals(78_000,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.STORE,"owner","VND").orElseThrow().getAvailableBalanceVnd());
        assertEquals(2_000,wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType.PLATFORM,"SCANMS","VND").orElseThrow().getAvailableBalanceVnd()); assertEquals(4,entries.count());
    }
}
