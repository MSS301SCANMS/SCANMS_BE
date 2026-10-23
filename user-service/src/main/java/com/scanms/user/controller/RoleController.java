package com.scanms.user.controller;

import com.scanms.user.constant.RoleName;
import com.scanms.user.dto.ApiResponse;
import com.scanms.user.service.RoleService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quan tri role: tao realm role tren Keycloak va cap/thu hoi role cua user.
 * Khong dung @RequestMapping o class vi dung 2 base path khac nhau
 * (/api/v1/roles cho danh muc role, /api/v1/users/{userId}/roles cho role cua user).
 */
@RestController
@Validated
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;

    @PostMapping("/api/v1/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> createRole(@RequestParam @NotBlank String roleName) {
        service.createNewRole(roleName);
        return ApiResponse.success("Tạo role mới thành công trên Keycloak!");
    }

    @PostMapping("/api/v1/users/{userId}/roles/shop-owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<String> grantShopOwnerRole(@PathVariable String userId) {
        service.grantShopOwnerRole(userId);
        return ApiResponse.success("SHOP_OWNER role granted");
    }

    @PostMapping("/api/v1/users/{userId}/roles/kol-ctv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<String> grantKolRole(@PathVariable String userId) {
        service.grantKolRole(userId);
        return ApiResponse.success("KOL_CTV role granted");
    }

    @DeleteMapping("/api/v1/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> removeRole(@PathVariable String userId,
                                         @RequestParam RoleName roleName) {
        service.removeRoleFromUser(userId, roleName);
        return ApiResponse.success("Đã thu hồi role của user!");
    }
}
