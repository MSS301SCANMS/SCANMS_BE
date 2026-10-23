package com.scanms.user.service;

public interface RoleService {
    void removeRoleFromUser(String userId, String roleName);
    void grantShopOwnerRole(String userId);
    void grantKolRole(String userId);
    void createNewRole(String roleName);
}
