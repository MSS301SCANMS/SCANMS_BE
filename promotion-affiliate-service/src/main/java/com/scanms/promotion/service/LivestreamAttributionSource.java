package com.scanms.promotion.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class LivestreamAttributionSource {
    @Value("${clients.product.url:http://localhost:8082}")
    private String productUrl;

    public Map<?, ?> session(String id, String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        try {
            var response = RestClient.builder().requestFactory(factory).build().get()
                    .uri(productUrl + "/api/v1/livestreams/{id}", id).headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
            if (response != null && response.get("result") instanceof Map<?, ?> live) return live;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot verify livestream attribution");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Livestream contract unavailable");
    }
}
