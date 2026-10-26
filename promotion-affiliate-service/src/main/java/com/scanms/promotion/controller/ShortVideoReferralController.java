package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.ShortVideoReferralService;
import com.scanms.promotion.service.ShortVideoReferralService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;

@RestController @RequiredArgsConstructor @org.springframework.validation.annotation.Validated
public class ShortVideoReferralController {
    private final ShortVideoReferralService service;
    @PostMapping("/api/v1/referrals/video-access")
    public ApiResponse<Map<String,String>> access(@RequestParam @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String storeId,
                                                @RequestBody @NotEmpty @Size(max=5) List<@Valid Link> links) {return ApiResponse.success(service.videoAccess(storeId,links));}
    @PostMapping("/api/v1/referrals/clicks")
    public ApiResponse<Map<String,String>> click(@Valid @RequestBody Click input) {return ApiResponse.created(service.click(input));}
    @GetMapping("/api/v1/referrals/clicks/{id}/verify")
    @PreAuthorize("@financeBoundary.isOrderService(authentication)")
    public ApiResponse<Map<String,String>> verify(@PathVariable String id,@RequestParam String productId,@RequestParam String customerId) {
        return ApiResponse.success(service.verifyClick(id,productId,customerId));
    }
}
