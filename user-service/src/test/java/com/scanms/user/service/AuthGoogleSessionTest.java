package com.scanms.user.service;

import com.scanms.user.dto.response.AuthResponse;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthGoogleSessionTest {
    @Test void googleExchangeIssuesRefreshCredentialsThatCanRenewTheSession() {
        var keycloakTokenService = mock(KeycloakTokenService.class);
        when(keycloakTokenService.getAdminAccessToken()).thenReturn("service-token");
        var service = new AuthServiceImpl(mock(UserRepository.class), mock(RoleRepository.class), mock(OtpService.class), mock(MailService.class), keycloakTokenService);
        ReflectionTestUtils.setField(service, "serverUrl", "https://identity.example.test");
        ReflectionTestUtils.setField(service, "realm", "fixture");
        ReflectionTestUtils.setField(service, "clientId", "fixture-client");
        ReflectionTestUtils.setField(service, "clientSecret", "fixture-secret");
        var server = MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate"));
        String url = "https://identity.example.test/realms/fixture/protocol/openid-connect/token";
        server.expect(requestTo(url)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("requested_subject=fixture-user")))
                .andExpect(content().string(containsString("requested_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Arefresh_token")))
                .andExpect(content().string(containsString("subject_token=service-token")))
                .andRespond(withSuccess("{\"access_token\":\"google-access\",\"refresh_token\":\"google-refresh\",\"expires_in\":300,\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=google-refresh")))
                .andRespond(withSuccess("{\"access_token\":\"renewed-access\",\"refresh_token\":\"rotated-refresh\",\"expires_in\":300,\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        var login = (AuthResponse) ReflectionTestUtils.invokeMethod(service, "getTokenForUser", "fixture-user");
        assertNotNull(login);
        assertEquals("google-refresh", login.refreshToken());
        var renewed = service.refreshToken(login.refreshToken());
        assertEquals("renewed-access", renewed.accessToken());
        assertEquals("rotated-refresh", renewed.refreshToken());
        server.verify();
    }
}
