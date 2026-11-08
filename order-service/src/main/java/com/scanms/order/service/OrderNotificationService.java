package com.scanms.order.service;

import com.scanms.order.dto.response.OrderNotificationPage;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface OrderNotificationService {
    OrderNotificationPage list(String category, int page, int limit, Jwt jwt);

    Map<String, Object> unread(Jwt jwt);

    void markRead(String notificationId, Jwt jwt);

    void markAllRead(Jwt jwt);
}
