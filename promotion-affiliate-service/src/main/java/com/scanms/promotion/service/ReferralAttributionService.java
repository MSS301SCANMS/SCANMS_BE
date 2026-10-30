package com.scanms.promotion.service;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface ReferralAttributionService {
    Map<String, String> verify(String referralLinkId, String productId, Jwt jwt);

    Map<String, String> livestream(String livestreamId, String productId, Jwt jwt);
}
