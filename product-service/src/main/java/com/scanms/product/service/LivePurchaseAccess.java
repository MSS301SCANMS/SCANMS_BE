package com.scanms.product.service;

import org.springframework.security.oauth2.jwt.Jwt;

public interface LivePurchaseAccess {
    String issue(String livestreamId, Jwt viewer);
    void verify(String token, String livestreamId, String identitySubject);
}
