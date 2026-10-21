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
        mvc.perform(post("/api/v1/auth/refresh-token").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"fixture-refresh\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.refresh_token").value("rotated-refresh"));
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/refresh-token")).andExpect(status().isUnauthorized());
    }
}
