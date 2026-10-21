package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.PartnershipInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/referrals/invitations")
@RequiredArgsConstructor
public class PartnershipInvitationController {
    private final PartnershipInvitationService service;

    public record Create(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String storeId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String collaboratorId,
            @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String productId,
            @Size(max = 2000) String message) {}

    public record Decision(@NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String decision) {}

    @GetMapping("/catalog")
    public ApiResponse<PartnershipInvitationService.Catalog> catalog(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.catalog(jwt));
    }

    @GetMapping("/shop")
    public ApiResponse<List<PartnershipInvitationService.View>> shop(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.shop(jwt));
    }

    @GetMapping("/mine")
    public ApiResponse<List<PartnershipInvitationService.View>> mine() {
        return ApiResponse.success(service.mine());
    }

    @PostMapping
    public ApiResponse<PartnershipInvitationService.View> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create r) {
        return ApiResponse.created(
                service.create(jwt, r.storeId(), r.collaboratorId(), r.productId(), r.message()));
    }

    @PostMapping("/{id}/respond")
    public ApiResponse<PartnershipInvitationService.View> respond(
            @PathVariable String id, @Valid @RequestBody Decision r) {
        return ApiResponse.success(service.respond(id, r.decision()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<PartnershipInvitationService.View> cancel(@PathVariable String id) {
        return ApiResponse.success(service.cancel(id));
    }
}
