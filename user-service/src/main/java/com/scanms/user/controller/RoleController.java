package com.scanms.user.controller;

import com.scanms.user.dto.ApiResponse;
import com.scanms.user.service.RoleService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> createRole(@RequestParam @NotBlank String roleName) {
        service.createNewRole(roleName);
        return ApiResponse.success("Tạo role mới thành công trên Keycloak!");
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> removeRole(
            @PathVariable String userId,
            @RequestParam String roleName) {
        service.removeRoleFromUser(userId, roleName);
        return ApiResponse.success("Đã thu hồi role của user!");
    }

    @PostMapping("/{userId}/shop-owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<String> grantShopOwnerRole(@PathVariable String userId) {
        service.grantShopOwnerRole(userId);
        return ApiResponse.success("SHOP_OWNER role granted");
    }

    @PostMapping("/{userId}/kol-ctv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<String> grantKolRole(@PathVariable String userId) {
        service.grantKolRole(userId);
        return ApiResponse.success("KOL_CTV role granted");
    }
}
