package com.scanms.user.service.impl;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    @Value("${keycloak.auth-server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Override
    public void createNewRole(String roleName) {
        String createRoleUrl = adminBase() + "/roles";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAdminAccessToken());

        Map<String, Object> rolePayload = Map.of(
                "name", roleName,
                "description", "Created via Admin API"
        );

        restTemplate.postForEntity(createRoleUrl, new HttpEntity<>(rolePayload, headers), Void.class);
    }

    @Override
    @Transactional
    public void grantShopOwnerRole(String userId) {
        grantRole(userId, RoleName.SHOP_OWNER);
    }

    @Override
    @Transactional
    public void grantKolRole(String userId) {
        grantRole(userId, RoleName.KOL_CTV);
    }

    /**
     * Gan realm role tren Keycloak (theo identitySubject) roi dong bo vao bang user_roles.
     * Chi cap cho tai khoan dang ACTIVE.
     */
    private void grantRole(String userId, RoleName roleName) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new AppException(ErrorCode.FORBIDDEN, "Active account required");
        Role role = roleRepository.findAllByNameIn(List.of(roleName)).stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, roleName + " role is not initialized"));
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(getAdminAccessToken());
            String base = adminBase();
            Map<?, ?> keycloakRole = fetchRealmRole(base, roleName, headers);
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(base + "/users/" + user.getIdentitySubject() + "/role-mappings/realm",
                    new HttpEntity<>(List.of(keycloakRole), headers), Void.class);
            user.getRoles().add(role);
            repository.save(user);
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "Cannot grant " + roleName + " role in Keycloak");
        }
    }

    /**
     * Thu hoi realm role tren Keycloak (theo identitySubject) roi xoa khoi bang user_roles —
     * nhan userId local giong cac API grant o tren, khong phai sub cua Keycloak.
     */
    @Override
    @Transactional
    public void removeRoleFromUser(String userId, RoleName roleName) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(getAdminAccessToken());
            String base = adminBase();
            Map<?, ?> keycloakRole = fetchRealmRole(base, roleName, headers);
            headers.setContentType(MediaType.APPLICATION_JSON);
            // RestTemplate.delete() khong gui duoc body -> phai dung exchange() voi HttpMethod.DELETE.
            restTemplate.exchange(base + "/users/" + user.getIdentitySubject() + "/role-mappings/realm",
                    HttpMethod.DELETE, new HttpEntity<>(List.of(keycloakRole), headers), Void.class);
            user.getRoles().removeIf(role -> role.getName() == roleName);
            repository.save(user);
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "Cannot revoke " + roleName + " role in Keycloak");
        }
    }

    private Map<?, ?> fetchRealmRole(String base, RoleName roleName, HttpHeaders headers) {
        Map<?, ?> keycloakRole = restTemplate.exchange(base + "/roles/" + roleName, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
        if (keycloakRole == null || keycloakRole.get("id") == null)
            throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, roleName + " role is missing in Keycloak");
        return keycloakRole;
    }

    private String adminBase() {
        return serverUrl + "/admin/realms/" + realm;
    }

    private String getAdminAccessToken() {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);

        return (String) response.getBody().get("access_token");
    }
}
