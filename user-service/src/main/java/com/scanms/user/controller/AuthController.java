package com.scanms.user.controller;

import com.scanms.user.config.RefreshTokenCookie;
import com.scanms.user.dto.ApiResponse;
import com.scanms.user.dto.request.LoginRequest;
import com.scanms.user.dto.request.RegisterRequest;
import com.scanms.user.dto.request.GoogleLoginRequest;
import com.scanms.user.dto.request.ForgotPasswordSendOtpRequest;
import com.scanms.user.dto.request.ForgotPasswordVerifyOtpRequest;
import com.scanms.user.dto.request.ForgotPasswordResetRequest;
import com.scanms.user.dto.response.AuthResponse;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService service;
    private final RefreshTokenCookie refreshCookie;
    @PostMapping("/register")
    public ApiResponse<String> register(@Valid @RequestBody RegisterRequest request) {
        service.register(request);
        return ApiResponse.success("Đăng ký tài khoản thành công!");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(service.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return withRefreshCookie(service.googleLogin(request));
    }

    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {

        service.changePassword(oldPassword, newPassword);
        return ApiResponse.success("Đổi mật khẩu thành công!");
    }
    /**
     * Huỷ phiên trên Keycloak bằng refresh token trong cookie, rồi luôn xoá cookie.
     * Không cần JWT: access token có thể đã hết hạn, và chỉ server mới xoá được cookie HttpOnly.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request) {
        if (!refreshCookie.hasCsrfHeader(request)) return csrfRejected();
        String refreshToken = refreshCookie.read(request);
        if (refreshToken != null) {
            try {
                service.logout(refreshToken);
            } catch (AppException ex) {
                // Phiên đã hết hạn/không còn trên Keycloak: vẫn coi như đăng xuất thành công.
                log.warn("Keycloak logout failed: {}", ex.getMessage());
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .body(ApiResponse.success("Đăng xuất thành công!"));
    }

    /** Đổi refresh token (đọc từ cookie HttpOnly) lấy access token mới; cookie được thay bằng refresh token mới. */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(HttpServletRequest request) {
        if (!refreshCookie.hasCsrfHeader(request)) return csrfRejected();
        String refreshToken = refreshCookie.read(request);
        if (refreshToken == null) return sessionExpired("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        try {
            return withRefreshCookie(service.refreshToken(refreshToken));
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHENTICATED) return sessionExpired(ex.getMessage());
            throw ex;
        }
    }

    /** Đặt refresh token vào cookie HttpOnly và bỏ nó khỏi body — FE chỉ nhận access token. */
    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(AuthResponse auth) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (auth.refreshToken() != null && !auth.refreshToken().isBlank()) {
            response.header(HttpHeaders.SET_COOKIE, refreshCookie.issue(auth.refreshToken()));
        }
        AuthResponse body = new AuthResponse(auth.accessToken(), null, auth.expiresIn(), auth.tokenType());
        return response.body(ApiResponse.success(body));
    }

    /** 401 + xoá cookie: refresh token không còn hợp lệ, FE phải đăng nhập lại. */
    private <T> ResponseEntity<ApiResponse<T>> sessionExpired(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED.getCode(), message));
    }

    private <T> ResponseEntity<ApiResponse<T>> csrfRejected() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(),
                        "Thiếu header " + RefreshTokenCookie.CSRF_HEADER));
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
