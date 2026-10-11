package com.scanms.product.service;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Store;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.repository.StoreRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class ProductAccess {
    private final StoreRepository stores;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;

    public String userId() {
        Jwt jwt = jwt();
        try {
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
            factory.setReadTimeout(Duration.ofSeconds(10));
            Map<?, ?> response = RestClient.builder().requestFactory(factory).build().get()
                    .uri(userUrl + "/api/v1/users/me")
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .retrieve().body(Map.class);
            if (response != null && response.get("result") instanceof Map<?, ?> user
                    && "ACTIVE".equals(user.get("status"))
                    && jwt.getSubject().equals(user.get("identitySubject"))
                    && user.get("userId") != null) return user.get("userId").toString();
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Cannot verify the current user");
        }
        throw new AppException(ErrorCode.FORBIDDEN, "Active account required");
    }

    public String bearerToken() { return jwt().getTokenValue(); }

    public void requireManager() {
        if (!isManager())
            throw new AppException(ErrorCode.FORBIDDEN, "Manager role required");
        userId();
    }

    public boolean isManager() { return roles().stream().anyMatch(Set.of("ADMIN", "MANAGER")::contains); }

    public Store requireApprovedStore(String storeId) {
        Store store = requireOwnedStore(storeId);
        if (store.getApprovalStatus() != StoreStatus.APPROVED)
            throw new AppException(ErrorCode.CONFLICT, "Store is not approved");
        return store;
    }

    public Store requireOwnedStore(String storeId) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found: " + storeId));
        if (!java.util.Objects.equals(store.getOwnerUserId(), userId()) && !roles().contains("ADMIN"))
            throw new AppException(ErrorCode.FORBIDDEN, "Store owner required");
        return store;
    }

    private Set<String> roles() {
        Set<String> result = new HashSet<>();
        Object realm = jwt().getClaim("realm_access");
        if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> values)
            values.forEach(value -> result.add(value.toString()));
        Object resources = jwt().getClaim("resource_access");
        if (resources instanceof Map<?, ?> map) map.values().forEach(resource -> {
            if (resource instanceof Map<?, ?> entry && entry.get("roles") instanceof Collection<?> values)
                values.forEach(value -> result.add(value.toString()));
        });
        return result;
    }

    private Jwt jwt() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)
            return authentication.getToken();
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
