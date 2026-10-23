package com.scanms.user.service;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.impl.RoleServiceImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ShopOwnerRoleGrantTest {
    @Test void grantsRealmRoleUsingIdentitySubjectAndUpdatesLocalRoles() {
        var users = mock(UserRepository.class);
        var roles = mock(RoleRepository.class);
        var service = new RoleServiceImpl(users, roles);
        ReflectionTestUtils.setField(service, "serverUrl", "http://keycloak.test");
        ReflectionTestUtils.setField(service, "realm", "scanms");
        ReflectionTestUtils.setField(service, "clientId", "scanms-client");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        var rest = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        assertNotNull(rest);
        var server = MockRestServiceServer.createServer(rest);
        server.expect(requestTo("http://keycloak.test/realms/scanms/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak.test/admin/realms/scanms/roles/SHOP_OWNER"))
                .andRespond(withSuccess("{\"id\":\"role-id\",\"name\":\"SHOP_OWNER\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak.test/admin/realms/scanms/users/keycloak-sub/role-mappings/realm"))
                .andRespond(withSuccess());

        var user = User.builder().userId("internal-id").identitySubject("keycloak-sub")
                .status(UserStatus.ACTIVE).roles(new HashSet<>()).build();
        when(users.findById("internal-id")).thenReturn(Optional.of(user));
        when(roles.findAllByNameIn(List.of(RoleName.SHOP_OWNER)))
                .thenReturn(Set.of(Role.builder().name(RoleName.SHOP_OWNER).build()));
        service.grantShopOwnerRole("internal-id");
        server.verify();
        assertTrue(user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.SHOP_OWNER));
        verify(users).save(user);
    }
}
