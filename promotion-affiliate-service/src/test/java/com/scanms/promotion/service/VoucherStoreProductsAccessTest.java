package com.scanms.promotion.service;

import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class VoucherStoreProductsAccessTest {
    @Test void catalogIsAuthorizedFilteredByStoreAndFailuresDoNotBecomeEmptyLists() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var catalog = new AtomicReference<>("{\"result\":[{\"productId\":\"own-product\",\"storeId\":\"own-shop\"},{\"productId\":\"foreign-product\",\"storeId\":\"other-shop\"}]}");
        var status = new AtomicInteger(200); var productReads = new AtomicInteger(); var badHeaders = new AtomicInteger();
        server.createContext("/api/v1/", exchange -> {
            if (!"Bearer fixture-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) badHeaders.incrementAndGet();
            String path = exchange.getRequestURI().getPath(), body; int code = 200;
            if (path.endsWith("/users/me")) body = "{\"result\":{\"userId\":\"owner\",\"identitySubject\":\"subject\",\"status\":\"ACTIVE\"}}";
            else if (path.endsWith("/stores/own-shop")) body = "{\"result\":{\"ownerUserId\":\"owner\",\"approvalStatus\":\"APPROVED\"}}";
            else if (path.endsWith("/products")) { productReads.incrementAndGet(); body = catalog.get(); code = status.get(); }
            else body = "{\"result\":{\"ownerUserId\":\"someone-else\",\"approvalStatus\":\"APPROVED\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("fixture-token")
                    .header("alg", "none").subject("subject").claim("realm_access", Map.of("roles", java.util.List.of("SHOP_OWNER"))).build()));
            var access = new VoucherAccess(); String url = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.setField(access, "userUrl", url); ReflectionTestUtils.setField(access, "productUrl", url);
            var rows = access.storeProducts("own-shop");
            assertEquals(1, rows.size()); assertEquals("own-product", rows.getFirst().get("productId"));
            int before = productReads.get();
            assertEquals(ErrorCode.FORBIDDEN, assertThrows(AppException.class, () -> access.storeProducts("other-shop")).getErrorCode());
            assertEquals(before, productReads.get());
            assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(AppException.class, () -> access.storeProducts("../store")).getErrorCode());
            catalog.set("{\"result\":[]}"); assertTrue(access.storeProducts("own-shop").isEmpty());
            catalog.set("{\"result\":{}}");
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> access.storeProducts("own-shop")).getStatusCode().value());
            status.set(500);
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> access.storeProducts("own-shop")).getStatusCode().value());
            assertEquals(0, badHeaders.get());
        } finally { SecurityContextHolder.clearContext(); server.stop(0); }
    }
}
