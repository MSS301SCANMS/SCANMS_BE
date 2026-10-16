package com.scanms.user.controller;

import com.scanms.user.dto.ApiResponse;
import com.scanms.user.dto.request.LoginRequest;
import com.scanms.user.dto.request.RefreshTokenRequest;
import com.scanms.user.dto.request.RegisterRequest;
import com.scanms.user.dto.request.GoogleLoginRequest;
import com.scanms.user.dto.request.ForgotPasswordSendOtpRequest;
import com.scanms.user.dto.request.ForgotPasswordVerifyOtpRequest;
import com.scanms.user.dto.request.ForgotPasswordResetRequest;
import com.scanms.user.dto.response.AuthResponse;
import com.scanms.user.service.AuthService;
import com.scanms.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        service.register(request);
        return ApiResponse.success("Đăng ký tài khoản thành công!");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = service.login(request);
        return ApiResponse.success(authResponse);
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse authResponse = service.googleLogin(request);
        return ApiResponse.success(authResponse);
    }

    @DeleteMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> removeRole(
            @PathVariable String userId,
            @RequestParam String roleName) {

        service.removeRoleFromUser(userId, roleName);
        return ApiResponse.success("Đã thu hồi role của user!");
    }
    @PostMapping("/{userId}/roles/shop-owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<String> grantShopOwnerRole(@PathVariable String userId) {
        service.grantShopOwnerRole(userId);
        return ApiResponse.success("SHOP_OWNER role granted");
    }
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> createRole(@RequestParam @NotBlank String roleName) {
        service.createNewRole(roleName);
        return ApiResponse.success("Tạo role mới thành công trên Keycloak!");
    }
    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {

        service.changePassword(oldPassword, newPassword);
        return ApiResponse.success("Đổi mật khẩu thành công!");
    }
    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestParam String refreshToken) {
        service.logout(refreshToken);
        return ApiResponse.success("Đăng xuất thành công!");
    }
    @PostMapping("/refresh-token")
    public ApiResponse<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = service.refreshToken(request.getRefreshToken());
        return ApiResponse.success(response);
    }

    @PostMapping("/forgot-password/send-otp")
    public ApiResponse<String> sendForgotPasswordOtp(@Valid @RequestBody ForgotPasswordSendOtpRequest request) {
        service.sendForgotPasswordOtp(request.email());
        return ApiResponse.success("Đã gửi mã OTP tới email của bạn!");
    }

    @PostMapping("/forgot-password/verify-otp")
    public ApiResponse<String> verifyForgotPasswordOtp(@Valid @RequestBody ForgotPasswordVerifyOtpRequest request) {
        service.verifyForgotPasswordOtp(request.email(), request.otp());
        return ApiResponse.success("Mã OTP hợp lệ!");
    }

    @PostMapping("/forgot-password/reset")
    public ApiResponse<String> resetPasswordWithOtp(@Valid @RequestBody ForgotPasswordResetRequest request) {
        service.resetPasswordWithOtp(request.email(), request.otp(), request.newPassword());
        return ApiResponse.success("Đặt lại mật khẩu thành công!");
    }

}
