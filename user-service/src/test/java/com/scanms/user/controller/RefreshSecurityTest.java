package com.scanms.user.controller;

import com.scanms.user.dto.response.AuthResponse;
import com.scanms.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.hamcrest.Matchers.*;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:refresh-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN",
        "cloudinary.cloud-name=test", "cloudinary.api-key=test", "cloudinary.api-secret=test",
        "google.client-id=test", "keycloak.client-secret=test"})
class RefreshSecurityTest {
    @Autowired WebApplicationContext context;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AuthService auth;
    @Test void refreshPostWorksWithoutExpiredBearerWhilePrivateApisStayProtected() throws Exception {
        when(auth.refreshToken("fixture-refresh")).thenReturn(new AuthResponse("access", "rotated-refresh", 300L, "Bearer"));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/auth/refresh-token").header("X-Requested-With", "XMLHttpRequest").cookie(new Cookie("scanms_rt", "fixture-refresh")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.access_token").value("access"))
                .andExpect(jsonPath("$.result.refresh_token").doesNotExist())
                .andExpect(header().string("Set-Cookie", allOf(containsString("scanms_rt=rotated-refresh"), containsString("HttpOnly"), containsString("Path=/api/v1/auth"))));
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/refresh-token")).andExpect(status().isUnauthorized());
    }

    @Test void refreshRequiresCsrfHeaderAndNeverFallsBackToBodyCredentials() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/auth/refresh-token").cookie(new Cookie("scanms_rt", "fixture-refresh")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh-token").header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/json").content("{\"refreshToken\":\"fixture-refresh\"}"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verifyNoInteractions(auth);
    }

    @Test void logoutRevokesCookieCredentialAndClearsItWithoutBearer() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/auth/logout").header("X-Requested-With", "XMLHttpRequest").cookie(new Cookie("scanms_rt", "fixture-refresh")))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", allOf(containsString("HttpOnly"), containsString("Max-Age=0"))));
        verify(auth).logout("fixture-refresh");
    }
}
