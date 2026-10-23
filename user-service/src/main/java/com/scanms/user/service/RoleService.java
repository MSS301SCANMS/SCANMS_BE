package com.scanms.user.service;

import com.scanms.user.constant.RoleName;

/** Quan ly realm role tren Keycloak va role cua user trong DB local. */
public interface RoleService {
    /** Tao mot realm role moi tren Keycloak. */
    void createNewRole(String roleName);

    /** Cap role SHOP_OWNER cho user (Keycloak + DB local). */
    void grantShopOwnerRole(String userId);

    /** Cap role KOL_CTV cho user (Keycloak + DB local). */
    void grantKolRole(String userId);

    /** Thu hoi mot role cua user (Keycloak + DB local). */
    void removeRoleFromUser(String userId, RoleName roleName);
}
