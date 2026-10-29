package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "promotion-affiliate-service", url = "${clients.promotion.url:http://localhost:8085}")
public interface PromotionClient {
    @PostMapping("/api/v1/finance/commission-adjustments/{id}")
    Map<String, Object> cancelCommission(@PathVariable("id") String id, @RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> input);

    @GetMapping("/api/v1/referrals/clicks/{id}/verify")
    Map<String, Object> verifyClick(@RequestHeader("Authorization") String authorization, @PathVariable String id,
                                    @RequestParam String productId, @RequestParam String customerId);

    @GetMapping("/api/v1/referrals/livestream-attribution/{id}")
    Map<String, Object> liveAttribution(@RequestHeader("Authorization") String authorization, @PathVariable String id, @RequestParam String productId);

    @GetMapping("/api/v1/vouchers/{id}")
    Map<String, Object> getVoucher(@PathVariable String id);
}
