package com.scanms.promotion.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ReferralProductAccess {
    @Value("${clients.product.url:http://localhost:8082}") private String productUrl;
    private final RestClient http;

    public ReferralProductAccess() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        http = RestClient.builder().requestFactory(factory).build();
    }

    public void requireAvailable(String productId) {
        if (productId == null || !productId.matches("[A-Za-z0-9_-]{1,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sản phẩm không hợp lệ.");
        var product = result("/api/v1/public/products/" + productId);
        if (!"ACTIVE".equals(product.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm hiện không mở bán.");
        String storeId = java.util.Objects.toString(product.get("storeId"), "");
        if (!storeId.matches("[A-Za-z0-9_-]{1,100}")) throw unavailable();
        var store = result("/api/v1/stores/public/id/" + storeId);
        if (!"APPROVED".equals(store.get("approvalStatus")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gian hàng chưa được duyệt hoặc đã tạm ngừng.");
    }

    private Map<?, ?> result(String path) {
        try {
            Map<?, ?> envelope = http.get().uri(productUrl + path).retrieve().body(Map.class);
            if (envelope != null && envelope.get("result") instanceof Map<?, ?> result) return result;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm hoặc gian hàng.");
        } catch (RestClientException ex) { throw unavailable(); }
        throw unavailable();
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa kiểm tra được sản phẩm. Vui lòng thử lại.");
    }
}
