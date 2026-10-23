package com.scanms.user.service;

import com.scanms.user.exception.*;
import com.scanms.user.repository.*;
import com.scanms.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AuthRefreshTest {
    AuthServiceImpl service;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        service = new AuthServiceImpl(mock(UserRepository.class), mock(RoleRepository.class), mock(OtpService.class), mock(MailService.class), mock(KeycloakTokenService.class));
        ReflectionTestUtils.setField(service, "serverUrl", "https://identity.example.test");
        ReflectionTestUtils.setField(service, "realm", "fixture");
        ReflectionTestUtils.setField(service, "clientId", "fixture-client");
        ReflectionTestUtils.setField(service, "clientSecret", "fixture-secret");
        server = MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate"));
    }
    void response(HttpStatus status, String body) {
        server.expect(requestTo("https://identity.example.test/realms/fixture/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=fixture-refresh")))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }
    @Test void refreshReturnsRotatedCredentialsAndExpiry() {
        response(HttpStatus.OK, "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":300,\"token_type\":\"Bearer\"}");
        var result = service.refreshToken("fixture-refresh");
        assertEquals("new-access", result.accessToken()); assertEquals("new-refresh", result.refreshToken()); assertEquals(300L, result.expiresIn());
        server.verify();
    }
    @Test void invalidGrantReturns401WithoutProviderDetails() {
        response(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\",\"error_description\":\"private-provider-detail\"}");
        var error = assertThrows(AppException.class, () -> service.refreshToken("fixture-refresh"));
        assertEquals(ErrorCode.UNAUTHENTICATED, error.getErrorCode());
        assertFalse(error.getMessage().contains("private-provider-detail")); server.verify();
    }
    @Test void providerOutageDoesNotBecomeInvalidSession() {
        response(HttpStatus.SERVICE_UNAVAILABLE, "{}");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.refreshToken("fixture-refresh")).getStatusCode().value()); server.verify();
    }
    @Test void badClientConfigurationDoesNotBecomeInvalidSession() {
        response(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\"}");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.refreshToken("fixture-refresh")).getStatusCode().value()); server.verify();
    }
    @Test void blankRefreshIsRejected() {
        assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(AppException.class, () -> service.refreshToken(" ")).getErrorCode());
    }
}
