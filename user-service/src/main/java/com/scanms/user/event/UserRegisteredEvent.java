package com.scanms.user.event;

import java.time.Instant;

/**
 * Phát ra khi một tài khoản mới được tạo (đăng ký thường hoặc lần đầu đăng nhập Google).
 * Payment-service dùng nó để tạo ví CUSTOMER ngay lập tức. {@code userId} là id nội bộ của
 * user-service, cũng là ownerRefId của ví khách hàng.
 */
public record UserRegisteredEvent(String userId, String identitySubject, String email, String source,
                                  Instant occurredAt) {
    public static final String TOPIC = "user.registered.v1";
    public static final String SOURCE_REGISTER = "REGISTER";
    public static final String SOURCE_GOOGLE = "GOOGLE";
}
