package com.scanms.promotion.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CollaborationAccessTest {
    @Test void validatesEnvelopeAndForwardsJwtAndPreservesAuthorizationFailures() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new java.util.concurrent.atomic.AtomicReference<>("{\"result\":[{\"storeId\":\"mine\",\"name\":\"Mine\"}]}");
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/api/v1/stores/mine", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            var access = new CollaborationAccess(); ReflectionTestUtils.setField(access, "productUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            var jwt = Jwt.withTokenValue("owner-token").header("alg", "RS256").subject("owner").build();
            assertEquals("mine", access.stores(jwt, false).getFirst().get("storeId"));
            assertEquals("Bearer owner-token", authorization.get());
            body.set("{\"result\":{\"items\":[]}}");
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> access.stores(jwt, false)).getStatusCode().value());
            body.set("{\"result\":[{}]}");
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> access.stores(jwt, false)).getStatusCode().value());
            status.set(403);
            assertEquals(403, assertThrows(ResponseStatusException.class, () -> access.stores(jwt, false)).getStatusCode().value());
        } finally { server.stop(0); }
    }
}
