package com.scanms.user.controller;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.dto.ApiResponse;
import com.scanms.user.dto.PageResponse;
import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.request.UpdateUserStatusRequest;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping("/me")
    ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.getCurrentUser(jwt.getSubject()));
    }

    @PatchMapping("/me")
    ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(service.updateCurrentUser(jwt.getSubject(), request));
    }

    // --- Quan tri: Tai khoan & Phan quyen (chi ADMIN) ---

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<PageResponse<UserResponse>> listUsersForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UserStatus status) {
        return ApiResponse.success(service.getAllUsersForAdmin(page, size, search, role, status));
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<UserResponse> getUserForAdmin(@PathVariable String userId) {
        return ApiResponse.success(service.getUserByIdForAdmin(userId));
    }

    @PatchMapping("/admin/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<UserResponse> updateUserStatusForAdmin(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(service.updateUserStatusForAdmin(userId, request));
    }
}
