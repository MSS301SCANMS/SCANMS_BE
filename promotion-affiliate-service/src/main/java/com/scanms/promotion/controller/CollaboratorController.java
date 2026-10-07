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

@RestController
@RequestMapping("/api/v1/collaborators")
@RequiredArgsConstructor
public class CollaboratorController {

    private final CollaboratorService service;

    /** KOL tự nộp đơn đăng ký — cần AFFILIATE_INTERNAL hoặc ADMIN */
    @PreAuthorize("@financeBoundary.canWrite(authentication)")
    @PostMapping
    ResponseEntity<ApiResponse<CollaboratorProfileResponse>> create(
            @Valid @RequestBody ApplyCollaboratorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<CollaboratorProfileResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<CollaboratorProfileResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    /**
     * MANAGER/ADMIN duyệt hồ sơ KOL — chuyển PENDING → APPROVED.
     * Sau approve, KOL mới được tạo ReferralLink và nhận commission.
     */
    @PreAuthorize("@financeBoundary.canManage(authentication)")
    @PatchMapping("/{id}/approve")
    ApiResponse<CollaboratorProfileResponse> approve(@PathVariable String id) {
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
    ApiResponse<CollaboratorProfileResponse> reject(
            @PathVariable String id,
            @Valid @RequestBody RejectRequest request) {
        return ApiResponse.success(service.reject(id, request.reason()));
    }
}
