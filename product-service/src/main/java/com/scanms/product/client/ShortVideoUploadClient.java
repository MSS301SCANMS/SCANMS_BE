package com.scanms.product.client;

import com.scanms.product.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name="short-video-upload",url="${clients.user.url:http://localhost:8081}")
public interface ShortVideoUploadClient {
    @GetMapping("/api/v1/upload/short-video/verify")
    ApiResponse<Map<String,Object>> verify(@RequestHeader("Authorization") String authorization,@RequestParam String publicId);
}
