package com.scanms.product.client;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.ShortVideoContracts.Link;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@FeignClient(name="short-video-affiliate",url="${clients.promotion.url:http://localhost:8085}")
public interface ShortVideoAffiliateClient {
    @PostMapping("/api/v1/referrals/video-access")
    ApiResponse<Map<String,String>> verify(@RequestHeader("Authorization") String authorization,
                                           @RequestParam String storeId,@RequestBody List<Link> links);
}
