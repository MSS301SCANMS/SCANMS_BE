package com.scanms.product.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "live-vouchers", url = "${clients.promotion.url:http://localhost:8085}")
public interface LiveVoucherClient {
    @GetMapping("/api/v1/vouchers/{id}")
    Map<String, Object> get(@RequestHeader("Authorization") String authorization, @PathVariable("id") String id);

    @GetMapping("/api/v1/vouchers/public/platform")
    Map<String, Object> platform(@RequestParam("storeId") String storeId);
}
