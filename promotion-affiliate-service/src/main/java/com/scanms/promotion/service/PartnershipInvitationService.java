package com.scanms.promotion.service;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;

public interface PartnershipInvitationService {
    record Creator(String id,
                   String name,
                   String platform,
                   long followers,
                   String channelUrl
    ) {
    }

    record Product(String id,
                   String name
    ) {
    }

    record Catalog(String storeId,
                   String storeName,
                   List<Product> products,
                   List<Creator> creators
    ) {
    }

    record View(String id, String storeId, String storeName,
                String collaboratorId, String collaboratorName,
                String productId, String productName, String message,
                String status, String referralLinkId,
                String referralToken, LocalDateTime createdAt,
                LocalDateTime respondedAt
    ) {
    }

    Catalog catalog(Jwt jwt);

    List<View> shop(Jwt jwt);

    List<View> mine();

    View create(Jwt jwt, String storeId, String collaboratorId, String productId, String message);

    View respond(String id, String decision);

    View cancel(String id);
}
