package com.scanms.payment.service;

import com.scanms.payment.constant.WalletOwnerType;
import com.scanms.payment.config.SecurityConfig;
import com.scanms.payment.exception.AppException;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String role) {
        Jwt jwt=Jwt.withTokenValue("token").header("alg","RS256").subject("identity-sub")
                .claim("realm_access",Map.of("roles",List.of(role))).build();
        SecurityContextHolder.getContext().setAuthentication(new SecurityConfig().jwtConverter().convert(jwt));
    }
    PaymentAccess access() {
        PaymentAccess service=spy(new PaymentAccess());
        doReturn(Map.of("userId","business-user-uuid","identitySubject","identity-sub","status","ACTIVE")).when(service).get(contains("/users/me"));
        return service;
    }
    @Test void customerOwnerIsBusinessUserUuidNotJwtSubjectOrClientClaim() {
        authenticate("USER"); var service=access();
        assertDoesNotThrow(() -> service.owner(WalletOwnerType.CUSTOMER,"business-user-uuid"));
        assertThrows(AppException.class,() -> service.owner(WalletOwnerType.CUSTOMER,"identity-sub"));
        assertThrows(AppException.class,() -> service.owner(WalletOwnerType.CUSTOMER,"other-user"));
        assertThrows(AppException.class,() -> service.owner(WalletOwnerType.PLATFORM,"SCANMS"));
    }
    @Test void inactiveOrMismatchedIdentityCannotOwnCustomerWallet() {
        authenticate("USER"); var service=access();
        doReturn(Map.of("userId","business-user-uuid","identitySubject","identity-sub","status","BLOCKED")).when(service).get(contains("/users/me"));
        assertThrows(AppException.class,() -> service.owner(WalletOwnerType.CUSTOMER,"business-user-uuid"));
        doReturn(Map.of("userId","business-user-uuid","identitySubject","another-sub","status","ACTIVE")).when(service).get(contains("/users/me"));
        assertThrows(AppException.class,() -> service.userId());
    }
    @Test void shopMustHaveRoleAndMatchingStoreOwner() {
        authenticate("SHOP_OWNER"); var service=access();
        doReturn(Map.of("ownerUserId","business-user-uuid")).when(service).get(contains("/stores/store"));
        assertDoesNotThrow(() -> service.owner(WalletOwnerType.STORE,"store"));
        doReturn(Map.of("ownerUserId","another-user")).when(service).get(contains("/stores/store"));
        assertThrows(AppException.class,() -> service.owner(WalletOwnerType.STORE,"store"));
        authenticate("USER"); assertThrows(AppException.class,() -> service.owner(WalletOwnerType.STORE,"store"));
    }
}
