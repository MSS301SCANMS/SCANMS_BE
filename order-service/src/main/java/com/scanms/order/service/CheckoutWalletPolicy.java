package com.scanms.order.service;

import org.springframework.security.oauth2.jwt.Jwt;

public interface CheckoutWalletPolicy {
    void requireFunds(Jwt jwt, long amountVnd);
}
