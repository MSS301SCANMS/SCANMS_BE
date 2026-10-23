package com.scanms.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import org.springframework.web.client.RestClientException;

/** Business identity and shop ownership are resolved from trusted services. */
@Service @RequiredArgsConstructor
public class CommerceAccess {
    private final ShopReturnAccess shops;
    @Value("${clients.user.url:http://localhost:8081}")
    private String userUrl;
    public boolean role(Jwt jwt,String role) {
        if(jwt==null) return false;
        Object realm=jwt.getClaim("realm_access");
        if(realm instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> roles && roles.contains(role)) return true;
        Object resources=jwt.getClaim("resource_access");
        return resources instanceof Map<?,?> m && m.values().stream().anyMatch(v -> v instanceof Map<?,?> r && r.get("roles") instanceof Collection<?> roles && roles.contains(role));
    }
    public boolean manager(Jwt jwt) {
        return role(jwt,"MANAGER") || role(jwt,"ADMIN") || role(jwt,"SYSTEM_ADMIN") || role(jwt,"SYSTEM_MANAGER");
    }

    public String customer(Jwt jwt) {
        if(jwt==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            Map<?,?> envelope=FinanceDispatch.client().get().uri(userUrl+"/api/v1/users")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
            if(envelope!=null && envelope.get("result") instanceof Map<?,?> user && "ACTIVE".equals(user.get("status"))
                && jwt.getSubject().equals(user.get("identitySubject")) && user.get("userId") instanceof String id) return id;
        } catch(RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cannot verify active customer profile");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active customer profile required");
    }

    public Set<String> stores(Jwt jwt) {
        return role(jwt,"SHOP_OWNER") ||
                role(jwt,"SHOP_MANAGER") ? shops.stores(jwt).keySet() : Set.of();
    }

    public void shop(Jwt jwt,String storeId) {
        if(!stores(jwt).contains(storeId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Seller order not found for this shop");
    }
}
