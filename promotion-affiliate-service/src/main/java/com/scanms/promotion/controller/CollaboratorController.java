package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.service.CollaboratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import com.scanms.promotion.service.VoucherAccess;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/collaborators")
@RequiredArgsConstructor
public class CollaboratorController {

    private final CollaboratorService service;
    private final VoucherAccess access;

    /** KOL tự nộp đơn đăng ký — user đã đăng nhập (PENDING chờ MANAGER/ADMIN duyệt) */
    @PreAuthorize("@financeBoundary.canApply(authentication)")
    @PostMapping
    public ResponseEntity<ApiResponse<CollaboratorProfileResponse>> create(
            @Valid @RequestBody ApplyCollaboratorRequest request) {
        if(!Objects.equals(request.userId(),access.userId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không thể nộp hồ sơ cho tài khoản khác.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CollaboratorProfileResponse> getById(@PathVariable String id) {
        var profile=service.getById(id);
        if(access.isManager())access.requireManager();
        else if(!Objects.equals(profile.userId(),access.userId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không có quyền xem hồ sơ này.");
        return ApiResponse.success(profile);
    }

    @GetMapping
    public ApiResponse<List<CollaboratorProfileResponse>> findAll() {
        if(access.isManager()) { access.requireManager(); return ApiResponse.success(service.findAll()); }
        // Public collaborator summaries never expose identity documents or banking details.
        return ApiResponse.success(service.findAll().stream().map(profile -> new CollaboratorProfileResponse(profile.collaboratorId(),profile.userId(),profile.approvalStatus(),profile.joinedAt(),profile.policyVersion(),profile.createdAt(),profile.updatedAt(),null,null)).toList());
    }

    /**
     * MANAGER/ADMIN duyệt hồ sơ KOL — chuyển PENDING → APPROVED.
     * Sau approve, KOL mới được tạo ReferralLink và nhận commission.
     */
    @PreAuthorize("@financeBoundary.canManage(authentication)")
    @PatchMapping("/{id}/approve")
    public ApiResponse<CollaboratorProfileResponse> approve(@PathVariable String id) {
        return ApiResponse.success(service.approve(id));
    }

    /** DTO nhận lý do từ chối — bắt buộc không được để trống */
    public record RejectRequest(@NotBlank String reason) {}

    /**
     * MANAGER/ADMIN từ chối hồ sơ KOL — chuyển PENDING → REJECTED.
     * KOL bị REJECTED không thể tạo ReferralLink.
     */
    @PreAuthorize("@financeBoundary.canManage(authentication)")
    @PatchMapping("/{id}/reject")
    public ApiResponse<CollaboratorProfileResponse> reject(
            @PathVariable String id,
            @Valid @RequestBody RejectRequest request) {
        return ApiResponse.success(service.reject(id, request.reason()));
    }
}
