package com.scanms.user.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh token của Keycloak được giữ trong cookie HttpOnly — JavaScript của FE không đọc được,
 * nên một lỗi XSS không thể lấy cắp phiên dài hạn.
 * <ul>
 *   <li>{@code HttpOnly}: không truy cập được từ {@code document.cookie}.</li>
 *   <li>{@code Secure}: chỉ gửi qua HTTPS (Chrome/Firefox vẫn chấp nhận trên http://localhost).</li>
 *   <li>{@code SameSite=Strict}: trang web khác không kích hoạt được cookie.</li>
 *   <li>{@code Path=/api/v1/auth}: chỉ gửi kèm khi gọi refresh/logout, không đi theo mọi API.</li>
 *   <li>{@code Max-Age}: theo thời hạn ({@code exp}) của chính refresh token.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookie {
    public static final String CSRF_HEADER = "X-Requested-With";

    private static final Pattern EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private final String name;
    private final String path;
    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final Duration fallbackMaxAge;

    public RefreshTokenCookie(
            @Value("${scanms.auth.refresh-cookie.name:scanms_rt}") String name,
            @Value("${scanms.auth.refresh-cookie.path:/api/v1/auth}") String path,
            @Value("${scanms.auth.refresh-cookie.secure:true}") boolean secure,
            @Value("${scanms.auth.refresh-cookie.same-site:Strict}") String sameSite,
            @Value("${scanms.auth.refresh-cookie.domain:}") String domain,
            @Value("${scanms.auth.refresh-cookie.fallback-max-age:10h}") Duration fallbackMaxAge) {
        this.name = name;
        this.path = path;
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain;
        this.fallbackMaxAge = fallbackMaxAge;
    }

    /** Cookie chứa refresh token, hết hạn cùng lúc với token. */
    public String issue(String refreshToken) {
        return base(refreshToken).maxAge(maxAge(refreshToken)).build().toString();
    }

    /** Cookie rỗng, Max-Age=0 — trình duyệt xoá ngay. Chỉ server mới xoá được cookie HttpOnly. */
    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    /** Refresh token trong request, hoặc null nếu không có. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Chống CSRF cho refresh/logout: trình duyệt chỉ gửi header tự đặt sau khi CORS preflight
     * cho phép origin (gateway chỉ cho phép localhost) — form/thẻ img của trang khác không gửi được.
     */
    public boolean hasCsrfHeader(HttpServletRequest request) {
        String value = request.getHeader(CSRF_HEADER);
        return value != null && !value.isBlank();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
        if (domain != null && !domain.isBlank()) builder.domain(domain);
        return builder;
    }

    /** Đọc {@code exp} trong phần payload của refresh token (JWT); không đọc được thì dùng giá trị mặc định. */
    private Duration maxAge(String refreshToken) {
        try {
            String[] parts = refreshToken.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                Matcher matcher = EXP.matcher(payload);
                if (matcher.find()) {
                    Duration remaining = Duration.between(Instant.now(), Instant.ofEpochSecond(Long.parseLong(matcher.group(1))));
                    if (!remaining.isNegative() && !remaining.isZero()) return remaining;
                }
            }
        } catch (RuntimeException ignored) {
            // Token không phải JWT chuẩn: dùng thời hạn mặc định.
        }
        return fallbackMaxAge;
    }
}
