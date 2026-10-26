package com.scanms.payment.event;

import com.scanms.payment.constant.WalletOwnerType;
import com.scanms.payment.service.FinanceService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserRegisteredConsumerTest {
    private final FinanceService finance = mock(FinanceService.class);
    private final UserRegisteredConsumer consumer = new UserRegisteredConsumer(finance, JsonMapper.builder().build());

    @Test
    void createsCustomerWalletForUserId() {
        consumer.onUserRegistered("{\"userId\":\"u-1\",\"identitySubject\":\"kc-1\",\"email\":\"a@b.c\",\"source\":\"REGISTER\"}");
        verify(finance).ensureWallet(WalletOwnerType.CUSTOMER, "u-1");
    }

    @Test
    void ignoresEventWithoutUserId() {
        consumer.onUserRegistered("{\"email\":\"a@b.c\"}");
        verify(finance, never()).ensureWallet(any(), anyString());
    }

    @Test
    void retriesOnceWhenWalletWasCreatedConcurrently() {
        when(finance.ensureWallet(WalletOwnerType.CUSTOMER, "u-2"))
                .thenThrow(new DataIntegrityViolationException("uk_wallet_owner_currency"))
                .thenReturn(null);
        consumer.onUserRegistered("{\"userId\":\"u-2\"}");
        verify(finance, times(2)).ensureWallet(WalletOwnerType.CUSTOMER, "u-2");
    }
}
