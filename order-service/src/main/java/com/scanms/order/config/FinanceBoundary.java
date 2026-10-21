package com.scanms.order.config;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;
@Component("financeBoundary")
public class FinanceBoundary {
    public boolean canWrite(Authentication authentication) {
        return roles(authentication).stream().anyMatch(Set.of("ADMIN","SYSTEM_ADMIN","ORDER_INTERNAL")::contains);
    }
    public boolean canDecideReturn(Authentication authentication) {
        return roles(authentication).stream().anyMatch(Set.of("ADMIN","SYSTEM_ADMIN","MANAGER","ORDER_INTERNAL")::contains);
    }
    /** Xem thống kê tổng hợp (dashboard admin/manager). */
    public boolean canViewStats(Authentication authentication) {
        return roles(authentication).stream().anyMatch(Set.of("ADMIN","SYSTEM_ADMIN","MANAGER")::contains);
    }
    private Set<String> roles(Authentication authentication) {
        if(!(authentication instanceof JwtAuthenticationToken auth)) return Set.of();
        Set<String> roles=new HashSet<>();
        Object realm=auth.getToken().getClaim("realm_access");
        if(realm instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
        Object resources=auth.getToken().getClaim("resource_access");
        if(resources instanceof Map<?,?> m) m.values().forEach(v -> { if(v instanceof Map<?,?> rm && rm.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString())); });
        return roles;
    }
}
