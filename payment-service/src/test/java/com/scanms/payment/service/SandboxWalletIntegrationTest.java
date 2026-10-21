package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.MoneyDtos.TopUpInput;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:sandbox-wallet;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.payment.sandbox-enabled=true",
    "scanms.payment.automatic-payouts=false", "scanms.payment.bank-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF", "debug=false"
})
class SandboxWalletIntegrationTest {
    @Autowired FinanceService finance;
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository entries;
    @Autowired Environment environment;
    @Autowired WebApplicationContext context;
    @MockitoBean PaymentAccess access;
    @MockitoBean PayosProvider provider;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void setup() {
        entries.deleteAll(); wallets.deleteAll();
        ReflectionTestUtils.setField(finance,"environment",environment);
        ReflectionTestUtils.setField(finance,"sandboxEnabled",true);
        when(access.userId()).thenReturn("customer");
    }

    @AfterEach void restore() {
        ReflectionTestUtils.setField(finance,"environment",environment);
        ReflectionTestUtils.setField(finance,"sandboxEnabled",true);
    }

    @Test void creditsLedgerOnceWithoutCallingPayos() {
        var wallet=finance.ensureWallet(WalletOwnerType.CUSTOMER,"customer");
        var command=new TopUpInput(wallet.getWalletId(),100_000L,UUID.randomUUID().toString());
        var first=finance.sandboxTopUp(command);
        var retry=finance.sandboxTopUp(command);
        assertEquals(first.transactionId(),retry.transactionId());
        assertEquals(100_000L,wallets.findById(wallet.getWalletId()).orElseThrow().getAvailableBalanceVnd());
        assertEquals(1,entries.count());
        assertEquals("TOPUP_DEMO",first.referenceType());
        assertEquals(WalletTransactionDirection.CREDIT,first.direction());
        verifyNoInteractions(provider);
    }

    @Test void changedAmountCannotReuseCommandKey() {
        var wallet=finance.ensureWallet(WalletOwnerType.CUSTOMER,"customer");
        finance.sandboxTopUp(new TopUpInput(wallet.getWalletId(),10_000L,"same"));
        assertThrows(AppException.class,()->finance.sandboxTopUp(new TopUpInput(wallet.getWalletId(),20_000L,"same")));
        assertEquals(10_000L,wallets.findById(wallet.getWalletId()).orElseThrow().getAvailableBalanceVnd());
    }

    @Test void cannotCreditAnotherOwnerOrStoreWallet() {
        for(var wallet:java.util.List.of(finance.ensureWallet(WalletOwnerType.CUSTOMER,"other"),finance.ensureWallet(WalletOwnerType.STORE,"store")))
            assertThrows(AppException.class,()->finance.sandboxTopUp(new TopUpInput(wallet.getWalletId(),10_000L,"foreign")));
        assertEquals(0,entries.count());
    }

    @Test void requiresExplicitFlagAndLocalProfileAndRejectsProduction() {
        var wallet=finance.ensureWallet(WalletOwnerType.CUSTOMER,"customer");
        var input=new TopUpInput(wallet.getWalletId(),10_000L,"disabled");
        ReflectionTestUtils.setField(finance,"sandboxEnabled",false);
        assertFalse(finance.sandboxTopUpAvailable());
        assertThrows(AppException.class,()->finance.sandboxTopUp(input));
        ReflectionTestUtils.setField(finance,"sandboxEnabled",true);
        for(var profiles:java.util.List.of(new String[]{},new String[]{"prod"},new String[]{"local","production"})) {
            var env=new MockEnvironment(); env.setActiveProfiles(profiles);
            ReflectionTestUtils.setField(finance,"environment",env);
            assertFalse(finance.sandboxTopUpAvailable());
            assertThrows(AppException.class,()->finance.sandboxTopUp(input));
        }
        assertEquals(0,entries.count());
    }

    @Test void validatesAmountsAndWalletStatus() {
        var wallet=finance.ensureWallet(WalletOwnerType.CUSTOMER,"customer");
        for(long amount:new long[]{0,9_999,50_000_001})
            assertThrows(AppException.class,()->finance.sandboxTopUp(new TopUpInput(wallet.getWalletId(),amount,"invalid")));
        wallet.setStatus(WalletStatus.FROZEN); wallets.saveAndFlush(wallet);
        assertThrows(AppException.class,()->finance.sandboxTopUp(new TopUpInput(wallet.getWalletId(),10_000L,"frozen")));
        assertEquals(0,entries.count());
    }

    @Test void endpointRequiresAuthentication() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/finance/sandbox/top-ups").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
