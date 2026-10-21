package com.scanms.order.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ShopReturnAccessTest {
    @Test void forwardsCurrentTokenAndNeverTreatsUnverifiedStoreDataAsAnEmptyQueue() throws Exception {
        var body=new AtomicReference<>("{\"result\":[{\"storeId\":\"store-a\",\"name\":\"Shop A\"}]}");
        var authorization=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/stores/mine",exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            try(var response=exchange.getResponseBody()) { response.write(bytes); }
        });
        server.start();
        try {
            var access=new ShopReturnAccess();
            ReflectionTestUtils.setField(access,"productUrl","http://127.0.0.1:"+server.getAddress().getPort());
            var jwt=Jwt.withTokenValue("shop-access-fixture").header("alg","RS256").subject("owner").build();
            assertEquals(Map.of("store-a","Shop A"),access.stores(jwt));
            assertEquals("Bearer shop-access-fixture",authorization.get());
            body.set("{\"result\":[]}"); assertTrue(access.stores(jwt).isEmpty());
            body.set("{\"error\":\"Unavailable\"}");
            assertEquals(503,assertThrows(ResponseStatusException.class,()->access.stores(jwt)).getStatusCode().value());
            assertEquals(401,assertThrows(ResponseStatusException.class,()->access.stores(null)).getStatusCode().value());
        } finally { server.stop(0); }
    }
}
