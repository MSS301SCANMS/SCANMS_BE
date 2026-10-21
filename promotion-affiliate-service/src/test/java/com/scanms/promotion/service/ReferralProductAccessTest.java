package com.scanms.promotion.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ReferralProductAccessTest {
    @Test void validatesProductAndStoreAndDistinguishesMissingFromUnavailableCatalog() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var product = new AtomicReference<>("{\"result\":{\"storeId\":\"shop\",\"status\":\"ACTIVE\"}}");
        var store = new AtomicReference<>("{\"result\":{\"approvalStatus\":\"APPROVED\"}}");
        var status = new AtomicInteger(200);
        server.createContext("/api/v1/", exchange -> {
            String body = exchange.getRequestURI().getPath().contains("/products/") ? product.get() : store.get();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            var access = new ReferralProductAccess();
            ReflectionTestUtils.setField(access, "productUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            assertDoesNotThrow(() -> access.requireAvailable("product"));
            store.set("{\"result\":{\"approvalStatus\":\"PENDING\"}}");
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> access.requireAvailable("product")).getStatusCode().value());
            product.set("{\"result\":{\"storeId\":\"shop\",\"status\":\"OUT_OF_STOCK\"}}");
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> access.requireAvailable("product")).getStatusCode().value());
            product.set("{\"result\":[]}");
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> access.requireAvailable("product")).getStatusCode().value());
            status.set(404);
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> access.requireAvailable("product")).getStatusCode().value());
            status.set(500);
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> access.requireAvailable("product")).getStatusCode().value());
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> access.requireAvailable("../product")).getStatusCode().value());
        } finally { server.stop(0); }
    }
}
