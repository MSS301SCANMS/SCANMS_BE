package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.entity.*;
import com.scanms.payment.mapper.WalletTransactionMapper;
import com.scanms.payment.repository.*;
import com.scanms.payment.service.impl.WalletTransactionServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WalletTransactionServiceImplTest {
    @Test
    void successfulDebitUpdatesWalletAndPersistsTransactionAtomically() {
        WalletRepository walletRepository = mock(WalletRepository.class);
        WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
        String walletId = java.util.UUID.randomUUID().toString();
        Wallet wallet = Wallet.builder()
                .walletId(walletId)
                .availableBalanceVnd(100_000L)
                .heldBalanceVnd(0L)
                .status(WalletStatus.ACTIVE)
                .build();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new WalletTransactionServiceImpl(
                transactionRepository, walletRepository, new WalletTransactionMapper());
        var request = new CreateWalletTransactionRequest(
                walletId, WalletTransactionType.ORDER_PAYMENT, WalletTransactionDirection.DEBIT,
                30_000L, WalletTransactionStatus.SUCCESS, "ORDER", java.util.UUID.randomUUID().toString(),
                "order-payment-1", "Order payment", null);

        var response = service.create(request);

        assertEquals(70_000L, wallet.getAvailableBalanceVnd());
        assertEquals(100_000L, response.balanceBeforeVnd());
        assertEquals(70_000L, response.balanceAfterVnd());
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(any(WalletTransaction.class));
    }
}
