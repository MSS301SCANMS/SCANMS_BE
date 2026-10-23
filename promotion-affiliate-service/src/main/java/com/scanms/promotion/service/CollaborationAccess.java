package com.scanms.promotion.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

/** Resolves store ownership through Product, never through browser-supplied store IDs. */
@Component
public class CollaborationAccess {
    @Value("${clients.product.url:http://localhost:8082}") private String productUrl;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;
    private final RestClient http;

    public CollaborationAccess() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        http = RestClient.builder().requestFactory(factory).build();
    }

    public List<Map<?, ?>> stores(Jwt jwt, boolean admin) {
        if (admin) {
            Object result = result(userUrl + "/api/v1/users", jwt);
            if (!(result instanceof Map<?, ?> user) || !"ACTIVE".equals(user.get("status"))
                    || !jwt.getSubject().equals(user.get("identitySubject")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được kích hoạt.");
        }
        var stores = list(productUrl + (admin ? "/api/v1/stores" : "/api/v1/stores/mine"), jwt);
        if (stores.stream().anyMatch(store -> !(store.get("storeId") instanceof String id) || id.isBlank())) throw unavailable();
        return stores;
    }

    public List<Map<?, ?>> products(Jwt jwt) { return list(productUrl + "/api/v1/products", jwt); }
    public List<Map<?, ?>> livestreams(Jwt jwt) { return list(productUrl + "/api/v1/livestreams", jwt); }

    private List<Map<?, ?>> list(String url, Jwt jwt) {
        Object result = result(url, jwt);
        if (!(result instanceof List<?> items)) throw unavailable();
        List<Map<?, ?>> rows = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) throw unavailable();
            rows.add(row);
        }
        return rows;
    }

    private Object result(String url, Jwt jwt) {
        try {
            Map<?, ?> envelope = http.get().uri(url).headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .retrieve().body(Map.class);
            if (envelope != null && envelope.get("result") != null) return envelope.get("result");
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
                throw new ResponseStatusException(ex.getStatusCode(), "Bạn không có quyền xem dữ liệu hợp tác.");
            throw unavailable();
        } catch (RestClientException ex) { throw unavailable(); }
        throw unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Không tải được dữ liệu gian hàng hoặc livestream. Vui lòng thử lại.");
    }
}
