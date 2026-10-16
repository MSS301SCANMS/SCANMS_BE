package com.scanms.product.service;

import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class ShopRoleGranter {
    private final ProductAccess access;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;

    public void grant(String userId) {
        try {
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
            factory.setReadTimeout(Duration.ofSeconds(10));
            RestClient.builder().requestFactory(factory).build().post()
                    .uri(userUrl + "/api/v1/auth/" + userId + "/roles/shop-owner")
                    .headers(headers -> headers.setBearerAuth(access.bearerToken()))
                    .body(java.util.Map.of()).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Cannot grant SHOP_OWNER role; store approval was rolled back");
        }
    }
}
