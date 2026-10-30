package com.scanms.ops;

import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

import java.util.Map;

final class OpsResponses {
    private OpsResponses() {
    }

    /** Trả null nếu được phép, ngược lại là phản hồi 401/403 để endpoint trả thẳng. */
    static WebEndpointResponse<Map<String, Object>> denied(OpsAccess.Decision decision) {
        return switch (decision) {
            case ALLOWED -> null;
            case UNAUTHENTICATED -> new WebEndpointResponse<>(Map.of("message", "Cần đăng nhập"), 401);
            case FORBIDDEN -> new WebEndpointResponse<>(Map.of("message", "Chỉ quản trị viên được xem"), 403);
        };
    }
}
