package com.scanms.user.service.impl;

import com.scanms.user.service.KeycloakAdminClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Goi truc tiep Keycloak Admin REST API de lay thong tin user/role thuc te
 * cho cac tai khoan chi ton tai tren Keycloak (chua co ban ghi local DB).
 */
@Service
public class KeycloakAdminClientImpl implements KeycloakAdminClient {

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
    public KeycloakUserInfo getUserInfo(String identitySubject) {
        String adminToken = getAdminAccessToken();
        String url = serverUrl + "/admin/realms/" + realm + "/users/" + identitySubject;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            return null;
        }

        return new KeycloakUserInfo(
                (String) body.get("username"),
                (String) body.get("email"),
                (String) body.get("firstName"),
                (String) body.get("lastName")
        );
    }

    @Override
    public List<String> getUserRealmRoleNames(String identitySubject) {
        String adminToken = getAdminAccessToken();
        String url = serverUrl + "/admin/realms/" + realm + "/users/" + identitySubject + "/role-mappings/realm";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }
        );

        List<String> roleNames = new ArrayList<>();
        if (response.getBody() != null) {
            for (Map<String, Object> role : response.getBody()) {
                Object name = role.get("name");
                if (name != null) {
                    roleNames.add(name.toString());
                }
            }
        }
        return roleNames;
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
