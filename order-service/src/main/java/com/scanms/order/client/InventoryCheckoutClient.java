package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "checkout-inventory", url = "${clients.product.url:http://localhost:8082}")
public interface InventoryCheckoutClient {
    @PostMapping("/api/v1/inventory/reservations/quote")
    Map<String, Object> quote(@RequestHeader("Authorization") String authorization,
                              @RequestHeader("X-Shipping-Fee-Per-Store-Vnd") long shippingFee,
                              @RequestBody Map<String, Object> input);

    @PostMapping("/api/v1/inventory/reservations/{orderId}")
    Map<String, Object> reserve(@PathVariable("orderId") String orderId,
                                @RequestHeader("Authorization") String authorization,
                                @RequestHeader("X-Shipping-Fee-Per-Store-Vnd") long shippingFee,
                                @RequestBody Map<String, Object> input);
}
