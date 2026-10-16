package com.scanms.user.service;

import java.util.List;

public interface KeycloakAdminClient {

    record KeycloakUserInfo(
            String username,
            String email,
            String firstName,
            String lastName
    ) {}

    /** Lay thong tin co ban cua user tren Keycloak theo identitySubject (sub cua JWT). */
    KeycloakUserInfo getUserInfo(String identitySubject);

    /** Lay danh sach ten cac realm role ma user dang co tren Keycloak. */
    List<String> getUserRealmRoleNames(String identitySubject);
}
