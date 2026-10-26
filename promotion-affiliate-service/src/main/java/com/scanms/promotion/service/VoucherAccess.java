package com.scanms.promotion.service;

import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Component
public class VoucherAccess {
    @Value("${clients.user.url:http://localhost:8081}")
    private String userUrl;
    @Value("${clients.product.url:http://localhost:8082}")
    private String productUrl;
    private final RestClient http;

    public VoucherAccess() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public String userId() {
        var user = result(userUrl + "/api/v1/users");
        if (!"ACTIVE".equals(user.get("status")) || !jwt().getSubject().equals(user.get("identitySubject")))
            throw new AppException(ErrorCode.FORBIDDEN, "Active account required");
        return user.get("userId").toString();
    }

    public void requireManager() {
        if (!isManager())
            throw new AppException(ErrorCode.FORBIDDEN, "Manager role required");
        userId();
    }

    public boolean isManager() {
        return roles().stream().anyMatch(Set.of("ADMIN", "MANAGER", "SYSTEM_ADMIN", "SYSTEM_MANAGER")::contains);
    }

    public boolean canReadAllVouchers() {
        if(roles().stream().anyMatch(Set.of("ORDER_INTERNAL","PAYMENT_INTERNAL","AFFILIATE_INTERNAL")::contains))return true;
        if(isManager()){requireManager();return true;}return false;
    }

    public Set<String> ownedStoreIds() {
        userId();
        try {
            Map<?,?> response=http.get().uri(productUrl+"/api/v1/stores/mine").headers(h->h.setBearerAuth(bearerToken())).retrieve().body(Map.class);
            if(response!=null&&response.get("result") instanceof List<?> rows){
                var ids=new HashSet<String>();
                for(Object row:rows)if(row instanceof Map<?,?> store&&store.get("storeId") instanceof String id)ids.add(id);
                return ids;
            }
        }catch(RestClientException ex){throw new AppException(ErrorCode.CONFLICT,"Cannot verify seller stores");}
        throw new AppException(ErrorCode.CONFLICT,"Invalid seller store response");
    }

    public String bearerToken() {
        return jwt().getTokenValue();
    }

    public boolean ownsStore(String storeId) {
        if (storeId == null) return false;
        return ownedStoreIds().contains(storeId);
    }

    public void requireStoreOwner(String storeId) {
        var store = approvedStore(storeId);
        if (!userId().equals(store.get("ownerUserId")) && !roles().contains("ADMIN"))
            throw new AppException(ErrorCode.FORBIDDEN, "Store owner required");
    }

    public void requireApprovedStore(String storeId) {
        var store=result(productUrl+"/api/v1/stores/public/id/"+storeId);
        if(!"APPROVED".equals(store.get("approvalStatus")))throw new AppException(ErrorCode.CONFLICT,"Store is not approved");
    }

    public List<Map<?, ?>> storeProducts(String storeId) {
        if (storeId == null || !storeId.matches("[A-Za-z0-9_-]{1,100}"))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Mã gian hàng không hợp lệ.");
        requireStoreOwner(storeId);
        try {
            Map<?, ?> response = http.get().uri(productUrl + "/api/v1/products")
                    .headers(h -> h.setBearerAuth(jwt().getTokenValue())).retrieve().body(Map.class);
            if (response != null && response.get("result") instanceof List<?> rows) {
                if (rows.stream().anyMatch(row -> !(row instanceof Map<?, ?> product)
                        || product.get("productId") == null || product.get("storeId") == null))
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dữ liệu sản phẩm chưa hợp lệ.");
                return rows.stream().<Map<?, ?>>map(row -> (Map<?, ?>) row)
                        .filter(product -> storeId.equals(product.get("storeId"))).toList();
            }
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa tải được sản phẩm của gian hàng.");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa tải được sản phẩm của gian hàng.");
    }

    private Map<?, ?> approvedStore(String storeId) {
        var store = result(productUrl + "/api/v1/stores/" + storeId);
        if (!"APPROVED".equals(store.get("approvalStatus")))
            throw new AppException(ErrorCode.CONFLICT, "Store is not approved");
        return store;
    }

    public void requireProductStore(String productId, String storeId) {
        if (!storeId.equals(result(productUrl + "/api/v1/products/" + productId).get("storeId")))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Product does not belong to the store");
    }

    public void requireLivestreamStore(String livestreamId, String storeId) {
        if (!storeId.equals(result(productUrl + "/api/v1/livestreams/" + livestreamId).get("storeId")))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Livestream does not belong to the store");
    }

    private Map<?, ?> result(String url) {
        try {
            Map<?, ?> response = http.get().uri(url).headers(h -> h.setBearerAuth(jwt().getTokenValue())).retrieve().body(Map.class);
            if (response != null && response.get("result") instanceof Map<?, ?> value) return value;
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Required service data is unavailable");
        }
        throw new AppException(ErrorCode.CONFLICT, "Required service data is unavailable");
    }

    private Set<String> roles() {
        Set<String> roles = new HashSet<>();
        Object realm = jwt().getClaim("realm_access");
        if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> values)
            values.forEach(value -> roles.add(value.toString()));
        Object resources = jwt().getClaim("resource_access");
        if (resources instanceof Map<?, ?> map) map.values().forEach(resource -> {
            if (resource instanceof Map<?, ?> entry && entry.get("roles") instanceof Collection<?> values)
                values.forEach(value -> roles.add(value.toString()));
        });
        return roles;
    }

    private Jwt jwt() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)
            return auth.getToken();
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
