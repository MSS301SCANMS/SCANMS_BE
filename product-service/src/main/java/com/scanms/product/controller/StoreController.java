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
import com.scanms.product.dto.request.UpdateStoreRequest;
import com.scanms.product.dto.response.StoreDashboardResponse;
import com.scanms.product.repository.StoreRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService service;
    private final StoreRepository stores;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;
    @GetMapping("/mine") public ApiResponse<List<Map<String,Object>>> mine(@AuthenticationPrincipal Jwt jwt) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()); factory.setReadTimeout(Duration.ofSeconds(10));
        Map<?,?> response=RestClient.builder().requestFactory(factory).build().get().uri(userUrl+"/api/v1/users").headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
        if(response==null || !(response.get("result") instanceof Map<?,?> user) || !"ACTIVE".equals(user.get("status")) || !jwt.getSubject().equals(user.get("identitySubject"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ApiResponse.success(stores.findByOwnerUserId(user.get("userId").toString()).stream().map(s -> Map.<String,Object>of("storeId",s.getStoreId(),"name",s.getName(),"approvalStatus",s.getApprovalStatus())).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<StoreResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/{id}/dashboard")
    public ApiResponse<StoreDashboardResponse> dashboard(@PathVariable String id) {
        return ApiResponse.success(service.dashboard(id));
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<StoreResponse> suspend(@PathVariable String id) {
        return ApiResponse.success(service.setSuspended(id, true));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<StoreResponse> activate(@PathVariable String id) {
        return ApiResponse.success(service.setSuspended(id, false));
    }

    @PatchMapping("/{id}")
    public ApiResponse<StoreResponse> update(@PathVariable String id, @Valid @RequestBody UpdateStoreRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @GetMapping
    public ApiResponse<List<StoreResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
