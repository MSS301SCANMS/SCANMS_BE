package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.CollaborationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class CollaborationController {
    private final CollaborationService service;

    @GetMapping("/shop-collaborations")
    @PreAuthorize("@financeBoundary.canViewShopCollaborations(authentication)")
    public ApiResponse<List<CollaborationService.View>> shop(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(jwt, false));
    }

    @GetMapping("/admin-collaborations")
    @PreAuthorize("@financeBoundary.canManage(authentication)")
    public ApiResponse<List<CollaborationService.View>> admin(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(jwt, true));
    }
}
