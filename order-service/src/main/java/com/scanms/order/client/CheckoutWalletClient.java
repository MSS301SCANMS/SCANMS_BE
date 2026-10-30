package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "checkout-wallet", url = "${clients.payment.url:http://localhost:8084}")
public interface CheckoutWalletClient {
    @GetMapping("/api/v1/finance/wallets/me")
    Map<String, Object> wallet(@RequestHeader("Authorization") String authorization, @RequestParam("ownerType") String ownerType);
}
