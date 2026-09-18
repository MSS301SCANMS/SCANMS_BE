package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import com.scanms.promotion.service.ReferralLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class ReferralLinkController {
    private final ReferralLinkService service;

    @PostMapping
    ResponseEntity<ApiResponse<ReferralLinkResponse>> create(@Valid @RequestBody CreateReferralLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<ReferralLinkResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<ReferralLinkResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
