package com.scanms.user.service;

import com.scanms.user.dto.request.LoginRequest;
import com.scanms.user.exception.*;
import com.scanms.user.repository.*;
import com.scanms.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AuthLoginTest {
    AuthServiceImpl service; MockRestServiceServer server;
    @BeforeEach void setup() {
        service=new AuthServiceImpl(mock(UserRepository.class),mock(RoleRepository.class),mock(OtpService.class),mock(MailService.class), mock(org.springframework.context.ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service,"serverUrl","https://identity.example.test"); ReflectionTestUtils.setField(service,"realm","fixture");
        ReflectionTestUtils.setField(service,"clientId","fixture-client"); ReflectionTestUtils.setField(service,"clientSecret","fixture-secret");
        server=MockRestServiceServer.createServer((RestTemplate)ReflectionTestUtils.getField(service,"restTemplate"));
    }
    LoginRequest request() { return new LoginRequest("test@example.test","fixture-password"); }
    void response(HttpStatus status,String body) {
        server.expect(requestTo("https://identity.example.test/realms/fixture/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }
    @Test void successfulLoginPreservesRefreshCredentials() {
        response(HttpStatus.OK,"{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":300,\"token_type\":\"Bearer\"}");
        var result=service.login(request()); assertEquals("refresh",result.refreshToken()); assertEquals(300,result.expiresIn()); server.verify();
    }
    @Test void wrongPasswordIs401AndDoesNotExposeProviderBody() {
        response(HttpStatus.BAD_REQUEST,"{\"error\":\"invalid_grant\",\"error_description\":\"private-detail\"}");
        var error=assertThrows(AppException.class,()->service.login(request())); assertEquals(ErrorCode.INVALID_CREDENTIALS,error.getErrorCode()); assertFalse(error.getMessage().contains("private-detail"));
    }
    @Test void wrongClientSecretIsServiceUnavailableNotBadUserPassword() {
        response(HttpStatus.UNAUTHORIZED,"{\"error\":\"invalid_client\",\"error_description\":\"private-detail\"}");
        var error=assertThrows(ResponseStatusException.class,()->service.login(request())); assertEquals(503,error.getStatusCode().value()); assertFalse(error.getReason().contains("private-detail"));
    }
    @Test void providerOutageIsServiceUnavailable() {
        response(HttpStatus.INTERNAL_SERVER_ERROR,"{}");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.login(request())).getStatusCode().value());
    }
}
