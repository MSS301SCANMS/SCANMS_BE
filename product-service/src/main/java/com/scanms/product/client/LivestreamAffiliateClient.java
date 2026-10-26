package com.scanms.product.client;

import com.scanms.product.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name="livestream-affiliate",url="${clients.promotion.url:http://localhost:8085}")
public interface LivestreamAffiliateClient {
    @GetMapping("/api/v1/referrals/livestream-access")
    Map<String,Object> verify(@RequestHeader("Authorization") String bearer,
            @RequestParam String storeId,@RequestParam String collaboratorId);
}
