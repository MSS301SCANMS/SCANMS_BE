package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.CollaborationAccess;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:collaborations;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class CollaborationIntegrationTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @Autowired ReferralLinkRepository referrals;
    @Autowired CollaboratorProfileRepository collaborators;
    @MockitoBean CollaborationAccess access;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        referrals.deleteAll(); collaborators.deleteAll();
        when(access.stores(any(), eq(false))).thenReturn(List.of(Map.of("storeId", "mine", "name", "My Store")));
        when(access.stores(any(), eq(true))).thenReturn(List.of(Map.of("storeId", "mine", "name", "My Store"), Map.of("storeId", "other", "name", "Other Store")));
        when(access.products(any())).thenReturn(List.of(Map.of("productId", "product", "storeId", "mine", "name", "My Product"), Map.of("productId", "other-product", "storeId", "other", "name", "Other Product")));
        when(access.livestreams(any())).thenReturn(List.of(
                Map.of("livestreamId", "live", "storeId", "mine", "title", "My Live", "status", "SCHEDULED", "collaboratorRefs", Map.of("collaboratorIds", List.of("kol-a", "kol-b", "kol-a")), "participantRefs", Map.of("inviteStatus", "ACCEPTED")),
                Map.of("livestreamId", "outside", "storeId", "other", "title", "Other Live", "status", "SCHEDULED", "collaboratorRefs", Map.of("collaboratorIds", List.of("kol-c")))));
    }

    void referral(String product, String live, LocalDateTime expiresAt) {
        referrals.saveAndFlush(ReferralLink.builder().collaboratorId("kol-a").productId(product).livestreamId(live).token("ref-" + product).status(ReferralLinkStatus.ACTIVE).expiresAt(expiresAt).build());
    }
    @Test void ownedStoreCombinesReferralsAndDistinctLiveInvitesWithoutLeakingOtherStores() throws Exception {
        referral("product", "outside", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        referral("other-product", null, null);
        mvc.perform(get("/api/v1/referrals/shop-collaborations").with(jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("SHOP_OWNER"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[?(@.kind == 'REFERRAL')].productName").value("My Product"))
                .andExpect(jsonPath("$.result[?(@.kind == 'REFERRAL')].status").value("EXPIRED"))
                .andExpect(jsonPath("$.result[?(@.kind == 'REFERRAL')].livestreamId").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.result[?(@.kind == 'LIVESTREAM')].inviteStatus").value(org.hamcrest.Matchers.containsInAnyOrder("ACCEPTED", "ACCEPTED")));
    }
    @Test void customerCannotReadShopDataAndShopCannotUseAdminScope() throws Exception {
        mvc.perform(get("/api/v1/referrals/shop-collaborations").with(jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("CUSTOMER")))))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/referrals/admin-collaborations").with(jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("SHOP_OWNER")))))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/referrals/shop-collaborations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(access);
    }
    @Test void managerSeesAllStoresAndEmptyStoreDoesNotFetchGlobalData() throws Exception {
        referral("other-product", null, null);
        mvc.perform(get("/api/v1/referrals/admin-collaborations").with(jwt().jwt(j -> j.claim("resource_access", Map.of("app", Map.of("roles", List.of("MANAGER")))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(4));
        clearInvocations(access);
        when(access.stores(any(), eq(false))).thenReturn(List.of());
        mvc.perform(get("/api/v1/referrals/shop-collaborations").with(jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("SHOP_MANAGER"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
        verify(access, never()).products(any()); verify(access, never()).livestreams(any());
    }
    @Test void unavailableCatalogIsAnErrorNotAnEmptyCollaborationList() throws Exception {
        when(access.products(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không tải được dữ liệu gian hàng."));
        mvc.perform(get("/api/v1/referrals/shop-collaborations").with(jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of("SHOP_OWNER"))))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Không tải được dữ liệu gian hàng."));
    }
}
