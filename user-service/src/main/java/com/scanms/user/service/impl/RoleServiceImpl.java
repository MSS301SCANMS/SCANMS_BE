package com.scanms.user.service.impl;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.KeycloakTokenService;
import com.scanms.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final KeycloakTokenService keycloakTokenService;

    @Value("${keycloak.auth-server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Override
    @Transactional
    public void grantShopOwnerRole(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new AppException(ErrorCode.FORBIDDEN, "Active account required");
        Role role = roleRepository.findAllByNameIn(List.of(RoleName.SHOP_OWNER)).stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "SHOP_OWNER role is not initialized"));
        try {
            String adminToken = keycloakTokenService.getAdminAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            String base = serverUrl + "/admin/realms/" + realm;
            Map<?, ?> keycloakRole = restTemplate.exchange(base + "/roles/SHOP_OWNER", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class).getBody();
            if (keycloakRole == null || keycloakRole.get("id") == null)
                throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "SHOP_OWNER role is missing in Keycloak");
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(base + "/users/" + user.getIdentitySubject() + "/role-mappings/realm",
                    new HttpEntity<>(List.of(keycloakRole), headers), Void.class);
            user.getRoles().add(role);
            repository.save(user);
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "Cannot grant SHOP_OWNER role in Keycloak");
        }
    }

    @Transactional
    @Override
    public void grantKolRole(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new AppException(ErrorCode.FORBIDDEN, "Active account required");
        Role role = roleRepository.findAllByNameIn(List.of(RoleName.KOL_CTV)).stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "KOL_CTV role is not initialized"));
        try {
            String adminToken = keycloakTokenService.getAdminAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            String base = serverUrl + "/admin/realms/" + realm;
            Map<?, ?> keycloakRole = restTemplate.exchange(base + "/roles/KOL_CTV", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class).getBody();
            if (keycloakRole == null || keycloakRole.get("id") == null)
                throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "KOL_CTV role is missing in Keycloak");
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(base + "/users/" + user.getIdentitySubject() + "/role-mappings/realm",
                    new HttpEntity<>(List.of(keycloakRole), headers), Void.class);
            user.getRoles().add(role);
            repository.save(user);
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "Cannot grant KOL_CTV role in Keycloak");
        }
    }

    @Override
    public void removeRoleFromUser(String userId, String roleName) {
        String adminToken = keycloakTokenService.getAdminAccessToken();

        try {
            // 1. Lấy thông tin role
            String getRoleUrl = serverUrl + "/admin/realms/" + realm + "/roles/" + roleName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    getRoleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class
            );

            if (roleResponse.getBody() != null) {
                Map<String, Object> roleData = roleResponse.getBody();

                // 2. Đường dẫn DELETE mapping role của Keycloak
                String removeRoleUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
                headers.setContentType(MediaType.APPLICATION_JSON);

                List<Map<String, Object>> rolePayload = List.of(roleData);

                // Dùng HttpEntity kết hợp HttpMethod.DELETE vì RestTemplate không hỗ trợ trực tiếp body trong delete()
                HttpEntity<List<Map<String, Object>>> requestEntity = new HttpEntity<>(rolePayload, headers);
                restTemplate.exchange(removeRoleUrl, HttpMethod.DELETE, requestEntity, Void.class);
            }
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Xóa role thất bại: " + e.getMessage());
        }
    }

    @Override
    public void createNewRole(String roleName) {
        String adminToken = keycloakTokenService.getAdminAccessToken();
        String createRoleUrl = serverUrl + "/admin/realms/" + realm + "/roles";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> rolePayload = Map.of(
                "name", roleName,
                "description", "Created via Admin API"
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(rolePayload, headers);
        restTemplate.postForEntity(createRoleUrl, entity, Void.class);
    }
}
