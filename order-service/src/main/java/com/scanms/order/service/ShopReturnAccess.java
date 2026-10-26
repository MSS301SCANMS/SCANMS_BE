package com.scanms.order.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ShopReturnAccess {
    @Value("${clients.product.url:http://localhost:8082}")
    private String productUrl;
    private final RestClient http = FinanceDispatch.client();

    public Map<String, String> stores(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            Map<?, ?> response = http.get().uri(productUrl + "/api/v1/stores/mine")
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
            if (response == null || !(response.get("result") instanceof List<?> list))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không xác minh được gian hàng. Vui lòng thử lại.");
            Map<String, String> stores = new LinkedHashMap<>();
            for (Object row : list)
                if (row instanceof Map<?, ?> store && store.get("storeId") instanceof String id && !id.isBlank())
                    stores.put(id, Objects.toString(store.get("name"), "Gian hàng"));
            return stores;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
                throw new ResponseStatusException(ex.getStatusCode(), "Không xác minh được quyền gian hàng.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ gian hàng chưa sẵn sàng. Vui lòng thử lại.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không kết nối được dịch vụ gian hàng. Vui lòng thử lại.");
        }
    }
}
