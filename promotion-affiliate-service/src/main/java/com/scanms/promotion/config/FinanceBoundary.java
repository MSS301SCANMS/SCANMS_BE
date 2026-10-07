package com.scanms.promotion.config;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.*;

@Component("financeBoundary")
public class FinanceBoundary {

    /** ADMIN / SYSTEM_ADMIN / AFFILIATE_INTERNAL — dùng cho các write operation nội bộ */
    public boolean canWrite(Authentication authentication) {
        return hasAnyRole(authentication, Set.of("ADMIN", "SYSTEM_ADMIN", "AFFILIATE_INTERNAL"));
    }

    /**
     * MANAGER / ADMIN / SYSTEM_MANAGER / SYSTEM_ADMIN — dùng cho approve/reject KOL,
     * duyệt store application và các quyết định nghiệp vụ cần cấp quản lý.
     */
    public boolean canManage(Authentication authentication) {
        return hasAnyRole(authentication,
                Set.of("MANAGER", "ADMIN", "SYSTEM_MANAGER", "SYSTEM_ADMIN"));
    }

    // ---------- helper ----------

    private boolean hasAnyRole(Authentication authentication, Set<String> allowed) {
        if (!(authentication instanceof JwtAuthenticationToken auth)) return false;
        Set<String> roles = extractRoles(auth);
        return roles.stream().anyMatch(allowed::contains);
    }

    private Set<String> extractRoles(JwtAuthenticationToken auth) {
        Set<String> roles = new HashSet<>();
        Object realm = auth.getToken().getClaim("realm_access");
        if (realm instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> list)
            list.forEach(r -> roles.add(r.toString()));
        Object resources = auth.getToken().getClaim("resource_access");
        if (resources instanceof Map<?, ?> m)
            m.values().forEach(v -> {
                if (v instanceof Map<?, ?> rm && rm.get("roles") instanceof Collection<?> list)
                    list.forEach(r -> roles.add(r.toString()));
            });
        return roles;
    }
}