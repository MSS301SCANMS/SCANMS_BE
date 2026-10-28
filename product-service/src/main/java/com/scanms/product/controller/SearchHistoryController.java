package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.SearchEventRequest;
import com.scanms.product.dto.response.SearchEventResponse;
import com.scanms.product.service.SearchHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchHistoryController {
    private final SearchHistoryService service;

    @PostMapping("/public/search-events")
    public ApiResponse<SearchEventResponse> record(@Valid @RequestBody SearchEventRequest input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.created(service.record(input, jwt));
    }

    @GetMapping("/search-history")
    public ApiResponse<List<SearchEventResponse>> recent(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.recent(jwt));
    }

    @DeleteMapping("/search-history")
    public ApiResponse<Boolean> clear(@AuthenticationPrincipal Jwt jwt) {
        service.clear(jwt);
        return ApiResponse.success(true);
    }
}
