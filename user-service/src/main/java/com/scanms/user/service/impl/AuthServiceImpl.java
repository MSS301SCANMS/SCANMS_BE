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
import com.scanms.user.service.MailService;
import com.scanms.user.service.OtpService;
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

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.scanms.user.dto.request.GoogleLoginRequest;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository repository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final RoleRepository roleRepository;
    private final OtpService otpService;
    private final MailService mailService;
    @Value("${keycloak.auth-server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${app.auth.email-verification-enabled:false}")
    private boolean emailVerificationEnabled;

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
                "emailVerified", !emailVerificationEnabled,
                "firstName", request.firstName(),
                "lastName",request.lastName(),
                "requiredActions", emailVerificationEnabled ? List.of("VERIFY_EMAIL") : List.of(),
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

                    if (emailVerificationEnabled) {
                        sendVerifyEmail(identitySubject, adminToken);
                    }
                                      // Lưu vào database của user-service
                    // Public registration must never grant business or administrator roles.
                    List<RoleName> rolesToQuery = List.of(RoleName.USER);
                    for (RoleName roleNameEnum : rolesToQuery) {
                        assignDefaultRoleToUser(identitySubject, adminToken, roleNameEnum.toString());
                    }
                    Set<Role> roles = roleRepository.findAllByNameIn(rolesToQuery);
                    User localUser = User.builder()
                            .identitySubject(identitySubject)
                            .username(request.username())
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
        body.add("username", request.email());
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
    @Transactional
    @Override
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        // 1. Verify Google ID Token
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.idToken());
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Token Google không hợp lệ: " + e.getMessage());
        }

        if (idToken == null) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Token Google không hợp lệ hoặc đã hết hạn");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String googleSub = payload.getSubject();

        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        // 2. Kiểm tra user đã tồn tại trong DB chưa
        User existingUser = repository.findByEmail(email).orElse(null);

        if (existingUser != null) {
            // User đã tồn tại -> login qua Keycloak bằng impersonation
            return getTokenForUser(existingUser.getIdentitySubject());
        }

        // 3. User chưa tồn tại -> tạo trên Keycloak + lưu DB
        String adminToken = getAdminAccessToken();
        String tempPassword = java.util.UUID.randomUUID().toString();

        String createUserUrl = serverUrl + "/admin/realms/" + realm + "/users";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> userPayload = Map.of(
                "username", email,
                "email", email,
                "enabled", true,
                "emailVerified", true,
                "firstName", firstName,
                "lastName", lastName,
                "requiredActions", List.of(),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", tempPassword,
                        "temporary", false
                ))
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(userPayload, headers);

        try {
            restTemplate.exchange(createUserUrl, HttpMethod.POST, entity, Void.class);

            // Lấy user ID từ Keycloak
            String getUserIdUrl = serverUrl + "/admin/realms/" + realm + "/users?email=" + email;
            ResponseEntity<List<Map<String, Object>>> userSearchResponse = restTemplate.exchange(
                    getUserIdUrl, HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (userSearchResponse.getBody() == null || userSearchResponse.getBody().isEmpty()) {
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không tìm thấy user vừa tạo trên Keycloak");
            }

            String identitySubject = (String) userSearchResponse.getBody().get(0).get("id");

            // Gán role USER mặc định
            assignDefaultRoleToUser(identitySubject, adminToken, RoleName.USER.toString());

            Set<Role> roles = roleRepository.findAllByNameIn(List.of(RoleName.USER));
            User newUser = User.builder()
                    .identitySubject(identitySubject)
                    .username(email.split("@")[0])
                    .email(email)
                    .fullName(lastName + " " + firstName)
                    .status(UserStatus.ACTIVE)
                    .roles(roles)
                    .build();
            repository.save(newUser);

            // Lấy token cho user mới
            return getTokenForUser(identitySubject);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Đăng nhập Google thất bại: " + e.getMessage());
        }
    }

    /**
     * Lấy token cho user thông qua Keycloak Token Exchange (impersonation)
     */
    private AuthResponse getTokenForUser(String userId) {
        String adminToken = getAdminAccessToken();
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        body.add("subject_token", adminToken);
        body.add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        body.add("requested_subject", userId);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            Map<String, Object> tokenData = response.getBody();

            if (tokenData != null) {
                return new AuthResponse(
                        (String) tokenData.get("access_token"),
                        (String) tokenData.get("refresh_token"),
                        tokenData.get("expires_in") != null ? ((Number) tokenData.get("expires_in")).longValue() : 300L,
                        (String) tokenData.get("token_type")
                );
            }
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không thể lấy token cho user: " + e.getMessage());
        }
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không thể lấy token cho user");
    }

    /**
     * Yêu cầu Keycloak gửi mail xác minh email tới user vừa tạo.
     * Chỉ được gọi khi app.auth.email-verification-enabled=true (đã cấu hình SMTP trong Keycloak).
     */
    private void sendVerifyEmail(String userId, String adminToken) {
        try {
            String sendVerifyEmailUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/send-verify-email";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            restTemplate.exchange(sendVerifyEmailUrl, HttpMethod.PUT, requestEntity, Void.class);
        } catch (Exception e) {
            // Không làm fail cả flow đăng ký nếu gửi mail lỗi (ví dụ SMTP chưa cấu hình) — chỉ log lại.
            System.err.println("Không thể gửi mail xác minh cho user [" + userId + "]: " + e.getMessage());
        }
    }

    @Override
    public void sendForgotPasswordOtp(String email) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Email chưa đăng ký tài khoản nào"));

        String otp = otpService.generate(user.getEmail());
        try {
            mailService.sendOtpEmail(user.getEmail(), otp);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không thể gửi mã OTP: " + e.getMessage());
        }
    }

    @Override
    public void verifyForgotPasswordOtp(String email, String otp) {
        if (!otpService.verify(email, otp)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Mã OTP không đúng hoặc đã hết hạn");
        }
    }

    @Override
    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        if (!otpService.consume(email, otp)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Mã OTP không đúng hoặc đã hết hạn");
        }

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Email chưa đăng ký tài khoản nào"));

        String adminToken = getAdminAccessToken();
        String resetPasswordUrl = serverUrl + "/admin/realms/" + realm + "/users/" + user.getIdentitySubject() + "/reset-password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> body = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            restTemplate.exchange(resetPasswordUrl, HttpMethod.PUT, entity, Void.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Đặt lại mật khẩu thất bại: " + e.getMessage());
        }
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
    @Transactional
    public void grantShopOwnerRole(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE)
            throw new AppException(ErrorCode.FORBIDDEN, "Active account required");
        Role role = roleRepository.findAllByNameIn(List.of(RoleName.SHOP_OWNER)).stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CONFLICT, "SHOP_OWNER role is not initialized"));
        try {
            String adminToken = getAdminAccessToken();
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
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new AppException(ErrorCode.KEYCLOAK_COMMUNICATION_ERROR, "Cannot grant SHOP_OWNER role in Keycloak");
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
