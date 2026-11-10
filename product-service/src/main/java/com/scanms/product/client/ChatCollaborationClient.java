package com.scanms.product.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "chat-collaboration", url = "${clients.promotion.url:http://localhost:8085}")
public interface ChatCollaborationClient {
    @GetMapping("/api/v1/referrals/chat/resources/{type}/{id}")
    Map<String, Object> resource(@RequestHeader("Authorization") String authorization,
                                 @PathVariable("type") String type, @PathVariable("id") String id);

    @GetMapping("/api/v1/referrals/chat/partner")
    Map<String, Object> partner(@RequestHeader("Authorization") String authorization,
                                @RequestParam("storeId") String storeId, @RequestParam("userId") String userId);
}
