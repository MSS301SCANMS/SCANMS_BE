package com.scanms.payment.service;

import com.scanms.payment.constant.WalletOwnerType;
import com.scanms.payment.entity.Wallet;
import com.scanms.payment.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.*;

/** Resolves business UUIDs from the authenticated identity, never a client owner claim. */
@Service
public class PaymentAccess {
    private final RestClient client = PaymentHttp.client();
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;
    @Value("${clients.product.url:http://localhost:8082}") private String productUrl;
    @Value("${clients.promotion.url:http://localhost:8085}") private String promotionUrl;
    public boolean operator() { return role("ADMIN") || role("MANAGER") || role("SYSTEM_ADMIN") || role("SYSTEM_MANAGER"); }
    public boolean internal() { return role("PAYMENT_INTERNAL"); }
    public boolean role(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    public String token() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) return auth.getToken().getTokenValue();
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
    public Map<String, Object> get(String url) {
        Map<?, ?> response = client.get().uri(url).headers(h -> h.setBearerAuth(token())).retrieve().body(Map.class);
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)) throw new AppException(ErrorCode.CONFLICT, "Business contract unavailable");
        @SuppressWarnings("unchecked") Map<String,Object> value = (Map<String,Object>) result;
        return value;
    }
    public String userId() {
        Map<String,Object> user=get(userUrl+"/api/v1/users/me");
        String id=Objects.toString(user.get("userId"),"");
        if(id.isBlank() || !"ACTIVE".equals(user.get("status"))) throw new AppException(ErrorCode.FORBIDDEN,"User profile is not active");
        if(user.get("identitySubject")!=null && SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth
                && !Objects.equals(user.get("identitySubject"),auth.getToken().getSubject())) throw new AppException(ErrorCode.FORBIDDEN,"Identity profile mismatch");
        return id;
    }
    public String collaboratorId() {
        Map<String,Object> profile = get(promotionUrl + "/api/v1/collaborators/me");
        if (!Set.of("APPROVED", "ACTIVE").contains(Objects.toString(profile.get("approvalStatus")))) throw new AppException(ErrorCode.FORBIDDEN, "Collaborator is not approved");
        return Objects.toString(profile.get("collaboratorId"));
    }
    public void owner(WalletOwnerType type, String id) {
        if (operator() || internal()) return;
        boolean allowed = switch (type) {
            case CUSTOMER -> Objects.equals(id, userId());
            case COLLABORATOR -> (role("KOL_CTV") || role("COLLABORATOR")) && Objects.equals(id, collaboratorId());
            case STORE -> (role("SHOP_OWNER") || role("SHOP_MANAGER")) && Objects.equals(userId(), get(productUrl + "/api/v1/stores/" + id).get("ownerUserId"));
            case PLATFORM -> false;
        };
        if (!allowed) throw new AppException(ErrorCode.FORBIDDEN);
    }
    public void wallet(Wallet w) { owner(w.getOwnerType(), w.getOwnerRefId()); }
    public void operatorOnly() { if (!operator()) throw new AppException(ErrorCode.FORBIDDEN); }
    public void trustedOnly() { if (!operator() && !internal()) throw new AppException(ErrorCode.FORBIDDEN); }
}
