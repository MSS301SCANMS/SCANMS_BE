package com.scanms.order.service;

import com.scanms.order.client.CheckoutWalletClient;
import com.scanms.order.service.impl.CheckoutWalletPolicyImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutWalletPolicyTest {
    @Test
    void checksFullAmountAndStopsInsufficientOrUnavailableWallets() {
        var client = mock(CheckoutWalletClient.class);
        var policy = new CheckoutWalletPolicyImpl(client);
        var jwt = Jwt.withTokenValue("buyer").header("alg", "RS256").subject("buyer").build();
        when(client.wallet("Bearer buyer", "CUSTOMER")).thenReturn(Map.of("result", Map.of("availableBalanceVnd", 200000L, "status", "ACTIVE")));
        assertDoesNotThrow(() -> policy.requireFunds(jwt, 200000));
        var error = assertThrows(ResponseStatusException.class, () -> policy.requireFunds(jwt, 250000));
        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("50.000"));
        when(client.wallet("Bearer buyer", "CUSTOMER")).thenReturn(Map.of("result", Map.of("availableBalanceVnd", 999999L, "status", "SUSPENDED")));
        assertThrows(ResponseStatusException.class, () -> policy.requireFunds(jwt, 1));
    }
}
