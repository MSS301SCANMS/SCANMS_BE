package com.scanms.gateway.ops;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Kiểm tra Gateway: chỉ ADMIN/SYSTEM_ADMIN vào được endpoint giám sát, route /api/v1/ops/{service}/ viết lại
 * thành /actuator/ của service đích (dùng một máy chủ giả làm user-service), và health vẫn công khai.
 */
@SpringBootTest
class GatewayOpsIntegrationTest {
    private static final AtomicReference<String> receivedPath = new AtomicReference<>();
    private static final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private static final HttpServer stub = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                receivedPath.set(exchange.getRequestURI().getPath() + (exchange.getRequestURI().getQuery() == null ? "" : "?" + exchange.getRequestURI().getQuery()));
                receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = "{\"service\":\"stub-user\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("USER_SERVICE_URL", () -> "http://localhost:" + stub.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    @Autowired ApplicationContext context;
    @MockitoBean ReactiveJwtDecoder decoder;
    WebTestClient client;

    @BeforeEach
    void setup() {
        receivedPath.set(null);
        receivedAuth.set(null);
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    private WebTestClient as(String role) {
        return client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void healthIsPublicWithoutDetails() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP").jsonPath("$.components").doesNotExist();
    }

    @Test
    void monitoringEndpointsRequireAnAdminToken() {
        client.get().uri("/actuator/scanmsroutes").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/ops/user/scanmsinfo").exchange().expectStatus().isUnauthorized();
        for (String role : new String[]{"USER", "MANAGER", "SHOP_OWNER"}) {
            as(role).get().uri("/actuator/scanmsroutes").exchange().expectStatus().isForbidden();
            as(role).get().uri("/actuator/scanmsinfo").exchange().expectStatus().isForbidden();
            as(role).get().uri("/actuator/scanmslogs").exchange().expectStatus().isForbidden();
            as(role).get().uri("/api/v1/ops/user/scanmsinfo").exchange().expectStatus().isForbidden();
        }
        org.junit.jupiter.api.Assertions.assertNull(receivedPath.get(), "yêu cầu bị chặn không được tới service");
    }

    @Test
    void adminSeesRoutesWithoutFiltersOrSecrets() {
        for (String role : new String[]{"ADMIN", "SYSTEM_ADMIN"}) {
            as(role).get().uri("/actuator/scanmsroutes").exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.count").value(c -> org.junit.jupiter.api.Assertions.assertTrue(((Number) c).intValue() >= 10))
                    .jsonPath("$.routes[*].id").value(hasItem("ops-user"))
                    .jsonPath("$.routes[*].id").value(hasItem("payment-service"))
                    .jsonPath("$.routes[?(@.id=='ops-payment')].target").value(hasItem(containsString("8084")))
                    .jsonPath("$.routes[0].filters").doesNotExist();
        }
    }

    @Test
    void gatewayReportsItsOwnProcessInfoAndLogs() {
        as("ADMIN").get().uri("/actuator/scanmsinfo").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.process.pid").isNumber().jsonPath("$.process.springBootVersion").isNotEmpty();
        as("ADMIN").get().uri("/actuator/scanmslogs?level=INFO").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.capacity").isEqualTo(300);
    }

    @Test
    void opsRoutesRewriteToTheServiceActuatorAndForwardTheToken() {
        // Đi qua đường JWT thật (bộ giải mã giả trả token có role ADMIN từ realm_access như Keycloak) để kiểm tra luôn
        // việc ánh xạ role của Gateway và việc chuyển tiếp header Authorization.
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("abc.def.ghi")
                .header("alg", "none").claim("sub", "admin-1")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("ADMIN"))).build();
        org.mockito.Mockito.when(decoder.decode("abc.def.ghi")).thenReturn(reactor.core.publisher.Mono.just(jwt));
        client.get().uri("/api/v1/ops/user/scanmslogs?level=ERROR").header("Authorization", "Bearer abc.def.ghi").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("stub-user");
        org.junit.jupiter.api.Assertions.assertEquals("/actuator/scanmslogs?level=ERROR", receivedPath.get());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer abc.def.ghi", receivedAuth.get());
    }

    @Test
    void otherActuatorEndpointsStayClosed() {
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/gateway/routes", "/actuator/heapdump"}) {
            as("ADMIN").get().uri(path).exchange().expectStatus().value(status ->
                    org.junit.jupiter.api.Assertions.assertNotEquals(200, status, path + " không được lộ ra"));
        }
        as("ADMIN").get().uri("/actuator/scanmsroutes").exchange().expectBody(String.class)
                .value(body -> org.hamcrest.MatcherAssert.assertThat(body, not(containsString("password"))));
    }
}
