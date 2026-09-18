package com.scanms.user.controller;

import com.scanms.user.dto.ApiResponse;
import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
