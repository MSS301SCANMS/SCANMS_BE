package com.scanms.promotion.service;

import com.scanms.promotion.dto.response.CommissionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Read delivery dates from Order only for commissions already authorized by CommissionService. */
@Service
public class CommissionTimelineService {
    @Value("${scanms.finance.token-url:}") private String tokenUrl;
    @Value("${scanms.finance.client-id:promotion-affiliate-service}") private String clientId;
    @Value("${scanms.finance.client-secret:}") private String secret;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;
    private final RestClient http;

    public CommissionTimelineService() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        http = RestClient.builder().requestFactory(factory).build();
    }

    public Map<String, Object> timelines(List<CommissionResponse> authorized) {
        if (authorized.isEmpty()) return Map.of();
        if (tokenUrl.isBlank() || secret.isBlank())
            throw new IllegalStateException("Chưa kết nối được thời hạn đối soát từ đơn hàng.");
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<?, ?> credentials = http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        if (credentials == null || !(credentials.get("access_token") instanceof String token))
            throw new IllegalStateException("Chưa kết nối được thời hạn đối soát từ đơn hàng.");
        List<String> ids = authorized.stream().map(CommissionResponse::orderItemId).distinct().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += 500) {
            Map<?, ?> response = http.post().uri(orderUrl + "/api/v1/finance/commission-timelines")
                    .headers(h -> h.setBearerAuth(token))
                    .body(Map.of("orderItemIds", ids.subList(start, Math.min(start + 500, ids.size()))))
                    .retrieve().body(Map.class);
            if (response == null || !(response.get("result") instanceof Map<?, ?> timelines))
                throw new IllegalStateException("Chưa tải được thời hạn đối soát từ đơn hàng.");
            timelines.forEach((key, value) -> result.put(key.toString(), value));
        }
        return result;
    }
}
