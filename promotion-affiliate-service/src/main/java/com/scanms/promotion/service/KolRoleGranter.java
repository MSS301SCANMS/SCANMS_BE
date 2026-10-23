package com.scanms.promotion.service;

import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KolRoleGranter {
    private final VoucherAccess access;
    private final RestClient http;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;

    public KolRoleGranter(VoucherAccess access) {
        this.access = access;
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public void grant(String userId) {
        try {
            http.post()
                    .uri(userUrl + "/api/v1/users/" + userId + "/roles/kol-ctv")
                    .headers(headers -> headers.setBearerAuth(access.bearerToken()))
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Cannot grant KOL_CTV role; collaborator approval was rolled back");
        }
    }
}
