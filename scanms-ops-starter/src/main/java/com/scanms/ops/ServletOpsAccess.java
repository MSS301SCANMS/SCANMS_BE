package com.scanms.ops;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Đọc role thẳng từ JWT của Keycloak ({@code realm_access.roles}, {@code resource_access.*.roles}) để không
 * phụ thuộc cách từng service ánh xạ authority (product, order, promotion hiện chưa có bộ ánh xạ role).
 */
public class ServletOpsAccess implements OpsAccess {
    private final Set<String> allowedRoles;

    public ServletOpsAccess(OpsProperties props) {
        this.allowedRoles = Set.copyOf(props.getAllowedRoles());
    }

    @Override
    public Decision check() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Decision.UNAUTHENTICATED;
        }
        Set<String> roles = new HashSet<>();
        if (auth instanceof JwtAuthenticationToken jwt) {
            if (jwt.getToken().getClaim("realm_access") instanceof Map<?, ?> realm) addRoles(roles, realm.get("roles"));
            if (jwt.getToken().getClaim("resource_access") instanceof Map<?, ?> resources) {
                resources.values().forEach(v -> {
                    if (v instanceof Map<?, ?> entry) addRoles(roles, entry.get("roles"));
                });
            }
        }
        auth.getAuthorities().forEach(a -> roles.add(a.getAuthority().replaceFirst("^ROLE_", "")));
        return roles.stream().anyMatch(allowedRoles::contains) ? Decision.ALLOWED : Decision.FORBIDDEN;
    }

    private static void addRoles(Set<String> out, Object value) {
        if (value instanceof Collection<?> list) list.forEach(r -> out.add(String.valueOf(r)));
    }
}
