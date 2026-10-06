package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import com.scanms.product.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService service;
    private final com.scanms.product.repository.StoreRepository stores;
    @org.springframework.beans.factory.annotation.Value("${clients.user.url:http://localhost:8081}") private String userUrl;
    @GetMapping("/mine") ApiResponse<List<Map<String,Object>>> mine(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()); factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        Map<?,?> response=org.springframework.web.client.RestClient.builder().requestFactory(factory).build().get().uri(userUrl+"/api/v1/users/me").headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
        if(response==null || !(response.get("result") instanceof Map<?,?> user) || !"ACTIVE".equals(user.get("status")) || !jwt.getSubject().equals(user.get("identitySubject"))) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return ApiResponse.success(stores.findByOwnerUserId(user.get("userId").toString()).stream().map(s -> Map.<String,Object>of("storeId",s.getStoreId(),"name",s.getName(),"approvalStatus",s.getApprovalStatus())).toList());
    }

    @PostMapping
    ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<StoreResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<StoreResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
