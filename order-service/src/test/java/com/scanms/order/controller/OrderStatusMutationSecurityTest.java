package com.scanms.order.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:status-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN",
        "scanms.finance.client-secret="
})
class OrderStatusMutationSecurityTest {
    @Value("${local.server.port}") int port;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void jwtTokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            String role = token.equals("manager-token") ? "MANAGER" : "USER";
            return Jwt.withTokenValue(token).header("alg", "RS256").subject("identity")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                    .claim("realm_access", Map.of("roles", List.of(role))).build();
        });
    }

    @Test void buyerCannotForgeDeliveryOrApproveReturn() {
        assertEquals(403, status(HttpMethod.PATCH, "/api/v1/seller-orders/absent/status?status=DELIVERED", "buyer-token"));
        assertEquals(403, status(HttpMethod.PATCH, "/api/v1/shipments/absent/status?status=DELIVERED", "buyer-token"));
        assertEquals(403, status(HttpMethod.POST, "/api/v1/returns/absent/decision?status=APPROVED", "buyer-token"));
    }

    @Test void managerCanReachReturnDecisionPolicy() {
        assertEquals(404, status(HttpMethod.POST, "/api/v1/returns/absent/decision?status=APPROVED", "manager-token"));
    }

    private int status(HttpMethod method, String path, String token) {
        try {
            return RestClient.create().method(method).uri("http://localhost:" + port + path)
                    .headers(headers -> headers.setBearerAuth(token)).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException exception) {
            return exception.getStatusCode().value();
        }
    }
}
