package com.scanms.user.service;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.mapper.UserMapper;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.KeycloakAdminClient;
import com.scanms.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserIdentitySyncTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final KeycloakAdminClient keycloakAdminClient = mock(KeycloakAdminClient.class);
    private final UserServiceImpl service = new UserServiceImpl(users, roles, keycloakAdminClient, new UserMapper());

    @BeforeEach
    void setup() {
        when(roles.findAllByNameIn(any())).thenAnswer(call -> {
            Collection<RoleName> names = call.getArgument(0);
            return names.stream().map(name -> Role.builder().name(name).build()).collect(Collectors.toSet());
        });
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            User user = call.getArgument(0);
            if (user.getUserId() == null) user.setUserId("internal-uuid");
            return user;
        });
    }

    private Jwt token(String... businessRoles) {
        return Jwt.withTokenValue("verified-by-resource-server").header("alg", "RS256")
                .subject("keycloak-sub").claim("email", "owner@example.test").claim("name", "Shop Owner")
                .claim("realm_access", Map.of("roles", java.util.List.of(businessRoles))).build();
    }

    @Test
    void provisionsInternalIdAndMultipleRolesFromSignedClaims() {
        when(users.findByIdentitySubject("keycloak-sub")).thenReturn(Optional.empty());
        var result = service.synchronizeCurrentUser(token("SHOP_OWNER", "KOL_CTV", "offline_access"));
        assertEquals("internal-uuid", result.userId());
        assertEquals("keycloak-sub", result.identitySubject());
        assertEquals(Set.of(RoleName.USER, RoleName.SHOP_OWNER, RoleName.KOL_CTV), result.roles());
    }

    @Test
    void refreshDoesNotDuplicateIdentityOrOverwriteEditedName() {
        var user = User.builder().userId("existing-uuid").identitySubject("keycloak-sub")
                .email("owner@example.test").fullName("Edited name").status(UserStatus.ACTIVE)
                .roles(Set.of(Role.builder().name(RoleName.USER).build())).build();
        when(users.findByIdentitySubject("keycloak-sub")).thenReturn(Optional.of(user));
        assertEquals("Edited name", service.synchronizeCurrentUser(token()).fullName());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void removesRevokedPrivilegedRole() {
        var user = User.builder().userId("existing-uuid").identitySubject("keycloak-sub")
                .email("owner@example.test").fullName("Owner").status(UserStatus.ACTIVE)
                .roles(Set.of(Role.builder().name(RoleName.ADMIN).build())).build();
        when(users.findByIdentitySubject("keycloak-sub")).thenReturn(Optional.of(user));
        assertEquals(Set.of(RoleName.USER), service.synchronizeCurrentUser(token()).roles());
    }

    @Test
    void suspendedAccountCannotBeReactivatedByLogin() {
        var user = User.builder().userId("existing-uuid").status(UserStatus.SUSPENDED).build();
        when(users.findByIdentitySubject("keycloak-sub")).thenReturn(Optional.of(user));
        assertThrows(AppException.class, () -> service.synchronizeCurrentUser(token("ADMIN")));
        verify(users, never()).saveAndFlush(any());
    }
}
