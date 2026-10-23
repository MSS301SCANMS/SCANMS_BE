package com.scanms.user.service;

public interface KeycloakTokenService {
    /** Lấy Admin Access Token từ Keycloak bằng client_credentials grant. */
    String getAdminAccessToken();
}
