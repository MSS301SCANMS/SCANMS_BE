package com.scanms.user.service.impl;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.dto.request.LoginRequest;
import com.scanms.user.dto.request.RegisterRequest;
import com.scanms.user.dto.response.AuthResponse;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository repository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final RoleRepository roleRepository;
    @Value("${keycloak.auth-server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Transactional
    @Override
    public void register(RegisterRequest request) {
        // Bước A: Lấy Admin Token để có quyền gọi API tạo user
        String adminToken = getAdminAccessToken();

        // Bước B: Chuẩn bị dữ liệu tạo user gửi sang Keycloak
        String createUserUrl = serverUrl + "/admin/realms/" + realm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> userPayload = Map.of(
                "username", request.username(),
                "email", request.email(),
                "enabled", true,
                "emailVerified", true,
                "firstName", request.firstName(),
                "lastName",request.lastName(),
                "requiredActions", List.of(),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", request.password(),
                        "temporary", false
                ))
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(userPayload, headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(createUserUrl, HttpMethod.POST, entity, Void.class);

            // Keycloak trả về status 201 Created khi thành công.
            if (response.getStatusCode().is2xxSuccessful()) {
                // Lấy lại user vừa tạo từ Keycloak để lấy identitySubject (sub)
                String getUserIdUrl = serverUrl + "/admin/realms/" + realm + "/users?username=" + request.username();

                // SỬ DỤNG ParameterizedTypeReference ĐỂ KHẮC PHỤC LỖI ÉP KIỂU
                ResponseEntity<List<Map<String, Object>>> userSearchResponse = restTemplate.exchange(
                        getUserIdUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );

                if (userSearchResponse.getBody() != null && !userSearchResponse.getBody().isEmpty()) {
                    Map<String, Object> kcUser = userSearchResponse.getBody().get(0);
                    String identitySubject = (String) kcUser.get("id");
                                      // Lưu vào database của user-service
                    List<RoleName> rolesToQuery = (request.roleNames() != null && !request.roleNames().isEmpty())
                            ? request.roleNames()
                            : List.of(RoleName.USER);
                    for (RoleName roleNameEnum : rolesToQuery) {
                        assignDefaultRoleToUser(identitySubject, adminToken, roleNameEnum.toString());
                    }
                    Set<Role> roles = roleRepository.findAllByNameIn(rolesToQuery);
                    User localUser = User.builder()
                            .identitySubject(identitySubject)
                            .email(request.email())
                            .fullName(request.lastName()+" "+request.firstName())
                            .status(UserStatus.ACTIVE)
                            .roles(roles)
                            .build();
                    repository.save(localUser);
                }
            }
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Đăng ký thất bại: " + e.getMessage());
        }
    }
    @Override
    public AuthResponse login(LoginRequest request) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "password");
        body.add("username", request.username());
        body.add("password", request.password());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            Map<String, Object> tokenData = response.getBody();

            if (tokenData != null) {
                return new AuthResponse(
                        (String) tokenData.get("access_token"),
                        (String) tokenData.get("refresh_token"),
                        ((Number) tokenData.get("expires_in")).longValue(),
                        (String) tokenData.get("token_type")
                );
            }
        } catch (Exception e) {
            System.err.println("Keycloak login error: " + e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Sai tên đăng nhập hoặc mật khẩu!");
        }
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Đăng nhập thất bại");
    }

    /**
     * Hàm phụ trợ lấy token Admin của Keycloak để thực hiện các thao tác quản trị
     */
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

    /**
     * Hàm phụ trợ gán Realm Role cho User trên Keycloak
     */
    private void assignDefaultRoleToUser(String userId, String adminToken, String roleName) {
        try {
            // 1. Lấy thông tin chi tiết của Role từ Keycloak dựa vào roleName
            String getRoleUrl = serverUrl + "/admin/realms/" + realm + "/roles/" + roleName;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    getRoleUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (roleResponse.getBody() != null) {
                Map<String, Object> roleData = roleResponse.getBody();

                // 2. Đường dẫn API gán Realm Role cho user
                String assignRoleUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

                headers.setContentType(MediaType.APPLICATION_JSON);

                // Payload yêu cầu là một List chứa thông tin role
                List<Map<String, Object>> rolePayload = List.of(roleData);

                HttpEntity<List<Map<String, Object>>> requestEntity = new HttpEntity<>(rolePayload, headers);

                restTemplate.postForEntity(assignRoleUrl, requestEntity, Void.class);
            }
        } catch (Exception e) {
            System.err.println("Không thể gán role [" + roleName + "] cho user trên Keycloak: " + e.getMessage());
        }
    }
    @Override
    public void removeRoleFromUser(String userId, String roleName) {
        String adminToken = getAdminAccessToken();

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
        String adminToken = getAdminAccessToken();
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
    @Override
    public void changePassword(String oldPassword, String newPassword) {
        // 1. Lấy thông tin Authentication hiện tại từ SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String accessToken = null;
        if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
            // Lấy chuỗi raw token (Bearer token) mà user đang gửi lên
            accessToken = jwtAuthToken.getToken().getTokenValue();
        }

        if (accessToken == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy thông tin xác thực của user");
        }

        // 2. Dùng accessToken này gọi sang Keycloak Account API
        String changePasswordUrl = serverUrl + "/realms/" + realm + "/account/credentials/password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken); // Gắn token vừa lấy vào

        Map<String, Object> body = Map.of(
                "currentPassword", oldPassword,
                "newPassword", newPassword,
                "confirmation", newPassword
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(changePasswordUrl, HttpMethod.PUT, entity, Void.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Đổi mật khẩu thất bại: " + e.getMessage());
        }
    }
    @Override
    public void logout(String refreshToken) {
        String logoutUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(logoutUrl, entity, Void.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Đăng xuất thất bại: " + e.getMessage());
        }
    }
    @Override
    public AuthResponse refreshToken(String refreshToken) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            Map<String, Object> bodyResponse = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && bodyResponse != null) {
                return AuthResponse.builder()
                        .accessToken((String) bodyResponse.get("access_token"))
                        .refreshToken((String) bodyResponse.get("refresh_token"))
                        .expiresIn(((Number) bodyResponse.get("expires_in")).longValue())
                        .tokenType((String) bodyResponse.get("token_type"))
                        .build();
            }
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không thể làm mới token");
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Refresh token không hợp lệ hoặc đã hết hạn: " + e.getMessage());
        }
    }
}
