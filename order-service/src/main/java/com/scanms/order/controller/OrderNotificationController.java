package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.response.OrderNotificationPage;
import com.scanms.order.service.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class OrderNotificationController {
    private final OrderNotificationService service;

    @GetMapping
    public ApiResponse<OrderNotificationPage> list(@RequestParam(defaultValue = "ALL") String category,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int limit,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(category, page, limit, jwt));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unread(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.unread(jwt));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(id, jwt);
        return ApiResponse.success(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(jwt);
        return ApiResponse.success(null);
    }
}
