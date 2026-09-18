package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "promotion-affiliate-service", url = "${clients.promotion.url:http://localhost:8085}")
public interface PromotionClient {
    @GetMapping("/api/v1/vouchers/{id}")
    Map<String, Object> getVoucher(@PathVariable UUID id);
}
