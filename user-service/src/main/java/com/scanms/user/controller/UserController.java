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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.synchronizeCurrentUser(jwt));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(service.updateCurrentUser(jwt.getSubject(), request));
    }

    // --- Quan tri: Tai khoan & Phan quyen (chi ADMIN) ---

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UserStatus status) {
        return ApiResponse.success(service.getAllUsers(page, size, search, role, status));
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> getUser(@PathVariable String userId) {
        return ApiResponse.success(service.getUserById(userId));
    }

    @PatchMapping("/admin/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(service.updateUserStatus(userId, request));
    }
}
