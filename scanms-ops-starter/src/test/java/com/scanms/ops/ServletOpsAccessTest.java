package com.scanms.ops;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServletOpsAccessTest {
    private final ServletOpsAccess access = new ServletOpsAccess(new OpsProperties());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static JwtAuthenticationToken token(Map<String, Object> claims) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        return new JwtAuthenticationToken(jwt, List.of());
    }

    @Test
    void noAuthenticationIsUnauthenticated() {
        assertEquals(OpsAccess.Decision.UNAUTHENTICATED, access.check());
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("k", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(OpsAccess.Decision.UNAUTHENTICATED, access.check());
    }

    @Test
    void adminAndSystemAdminRealmRolesAreAllowed() {
        SecurityContextHolder.getContext().setAuthentication(token(Map.of("sub", "1", "realm_access", Map.of("roles", List.of("USER", "ADMIN")))));
        assertEquals(OpsAccess.Decision.ALLOWED, access.check());
        SecurityContextHolder.getContext().setAuthentication(token(Map.of("sub", "1", "realm_access", Map.of("roles", List.of("SYSTEM_ADMIN")))));
        assertEquals(OpsAccess.Decision.ALLOWED, access.check());
    }

    @Test
    void managerAndPlainUsersAreForbidden() {
        SecurityContextHolder.getContext().setAuthentication(token(Map.of("sub", "1", "realm_access", Map.of("roles", List.of("MANAGER", "USER")))));
        assertEquals(OpsAccess.Decision.FORBIDDEN, access.check());
        SecurityContextHolder.getContext().setAuthentication(token(Map.of("sub", "1")));
        assertEquals(OpsAccess.Decision.FORBIDDEN, access.check());
    }

    @Test
    void clientRolesAreAlsoRead() {
        SecurityContextHolder.getContext().setAuthentication(token(Map.of("sub", "1",
                "resource_access", Map.of("scanms-client", Map.of("roles", List.of("ADMIN"))))));
        assertEquals(OpsAccess.Decision.ALLOWED, access.check());
    }
}
