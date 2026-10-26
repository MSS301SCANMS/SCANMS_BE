package com.scanms.product.service;

import org.springframework.security.oauth2.jwt.Jwt;

public interface LivestreamReactionService {
    long updateCount(String livestreamId, long likeCount, Jwt actor);
}
