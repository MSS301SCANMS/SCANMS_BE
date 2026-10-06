package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "payment-service", url = "${clients.payment.url:http://localhost:8084}")
public interface PaymentClient {
    @PostMapping("/api/v1/wallet-transactions")
    Map<String, Object> createWalletTransaction(@RequestBody Map<String, Object> request);
}
