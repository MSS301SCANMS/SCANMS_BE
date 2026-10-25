package com.scanms.promotion.service;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface CollaborationService {
    record View(String id, String kind, String collaboratorId, String collaboratorName,
                String collaboratorStatus, String storeId, String storeName, String productId, String productName,
                String livestreamId, String livestreamTitle, String token, String status, String inviteStatus,
                String createdAt, String scheduledAt, String expiresAt) {
    }

    List<View> list(Jwt jwt, boolean admin);
}
