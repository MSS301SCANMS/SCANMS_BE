package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "cancellation-payment", url = "${clients.payment.url:http://localhost:8084}")
public interface CancellationPaymentClient {
    @PostMapping("/api/v1/finance/order-cancellations/{id}/refund")
    Map<String, Object> refund(@PathVariable("id") String id, @RequestHeader("Authorization") String authorization);
}
