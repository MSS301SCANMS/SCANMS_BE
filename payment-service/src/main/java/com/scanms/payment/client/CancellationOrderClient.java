package com.scanms.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "cancellation-order", url = "${clients.order.url:http://localhost:8083}")
public interface CancellationOrderClient {
    @GetMapping("/api/v1/finance/orders/{id}/cancellation-refund-basis")
    Map<String, Object> basis(@PathVariable("id") String id, @RequestHeader("Authorization") String authorization);
}
