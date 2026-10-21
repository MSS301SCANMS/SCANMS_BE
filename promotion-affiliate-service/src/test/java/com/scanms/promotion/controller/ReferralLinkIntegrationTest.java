package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.ReferralProductAccess;
import com.scanms.promotion.service.VoucherAccess;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:referrals;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class ReferralLinkIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired ReferralLinkRepository links;
    @Autowired CollaboratorProfileRepository profiles;
    @MockitoBean VoucherAccess access;
    @MockitoBean ReferralProductAccess products;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    CollaboratorProfile mine;
    final String body = "{\"productId\":\"product-one\",\"token\":\"retry-token-123\",\"label\":\"My product\",\"channel\":\"TIKTOK\",\"status\":\"ACTIVE\"}";

    @BeforeEach void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        links.deleteAll(); profiles.deleteAll();
        mine = profiles.saveAndFlush(CollaboratorProfile.builder().userId("current-user").approvalStatus(CollaboratorStatus.APPROVED).build());
        when(access.userId()).thenReturn("current-user");
        when(access.isManager()).thenReturn(false);
    }
    @Test void createUsesAuthenticatedProfileSavesFieldsAndRetryDoesNotDuplicate() throws Exception {
        for (int i=0; i<2; i++) mvc.perform(post("/api/v1/referrals").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.result.collaboratorId").value(mine.getCollaboratorId()))
                .andExpect(jsonPath("$.result.label").value("My product")).andExpect(jsonPath("$.result.channel").value("TIKTOK"));
        assertEquals(1, links.count());
        verify(products, times(1)).requireAvailable("product-one");
        mvc.perform(post("/api/v1/referrals").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body.replace("My product", "Changed label")))
                .andExpect(status().isConflict());
        assertEquals(1, links.count());
    }
    @Test void authenticationOwnershipAndApprovalAreRequired() throws Exception {
        mvc.perform(post("/api/v1/referrals").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/referrals").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body.replace("{", "{\"collaboratorId\":\"someone-else\","))).andExpect(status().isForbidden());
        mine.setApprovalStatus(CollaboratorStatus.PENDING); profiles.saveAndFlush(mine);
        mvc.perform(post("/api/v1/referrals").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertEquals(0, links.count()); verifyNoInteractions(products);
    }
    @Test void validationRejectsUnsafeOrUnsupportedValuesBeforeWriting() throws Exception {
        for (String invalid : new String[]{ body.replace("product-one", "../product"), body.replace("TIKTOK", "UNKNOWN"),
                body.replace("My product", "  "), body.replace("retry-token-123", "bad token"), body.replace("ACTIVE", "INACTIVE"),
                body.replace("{", "{\"livestreamId\":\"live-one\","), body.replace("{", "{\"expiresAt\":\"2000-01-01T00:00:00\",") })
            mvc.perform(post("/api/v1/referrals").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        assertEquals(0, links.count()); verifyNoInteractions(products);
    }
    @Test void catalogFailureDoesNotCreateALink() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog unavailable")).when(products).requireAvailable("product-one");
        mvc.perform(post("/api/v1/referrals").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isServiceUnavailable());
        assertEquals(0, links.count());
    }
    ReferralLink link(String owner, String token) {
        return links.saveAndFlush(ReferralLink.builder().collaboratorId(owner).productId("product-one").token(token).status(ReferralLinkStatus.ACTIVE).build());
    }
    @Test void ownListAndDetailDoNotExposeAnotherKolLinks() throws Exception {
        link(mine.getCollaboratorId(), "mine-token");
        var other = link("other-profile", "other-token");
        mvc.perform(get("/api/v1/referrals").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1));
        mvc.perform(get("/api/v1/referrals/" + other.getReferralLinkId()).with(jwt())).andExpect(status().isNotFound());
    }
    @Test void guestCanResolveActiveLinkButCannotUseExpiredInactiveOrUnapprovedLink() throws Exception {
        var link = link(mine.getCollaboratorId(), "public-token");
        mvc.perform(get("/api/v1/referrals/resolve/public-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.productId").value("product-one")).andExpect(jsonPath("$.result.referralLinkId").value(link.getReferralLinkId()));
        mvc.perform(post("/api/v1/referrals/resolve/public-token")).andExpect(status().isUnauthorized());
        link.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)); links.saveAndFlush(link);
        mvc.perform(get("/api/v1/referrals/resolve/public-token")).andExpect(status().isGone());
        link.setExpiresAt(null); link.setStatus(ReferralLinkStatus.INACTIVE); links.saveAndFlush(link);
        mvc.perform(get("/api/v1/referrals/resolve/public-token")).andExpect(status().isGone());
        link.setStatus(ReferralLinkStatus.ACTIVE); links.saveAndFlush(link);
        mine.setApprovalStatus(CollaboratorStatus.SUSPENDED); profiles.saveAndFlush(mine);
        mvc.perform(get("/api/v1/referrals/resolve/public-token")).andExpect(status().isGone());
        mvc.perform(get("/api/v1/referrals/resolve/missing-token")).andExpect(status().isNotFound());
    }

    @Test void shopOwnerSeesAllKolLinksForOwnProductsWithoutRequiringAKolProfile() throws Exception {
        link(mine.getCollaboratorId(), "first-kol-token");
        link("other-profile", "second-kol-token");
        var foreign = link("foreign-profile", "foreign-product-token");
        foreign.setProductId("foreign-product"); links.saveAndFlush(foreign);
        when(access.userId()).thenReturn("shop-owner-without-kol-profile");
        when(access.storeProducts("shop-one")).thenReturn(java.util.List.of(java.util.Map.of(
                "productId", "product-one", "storeId", "shop-one", "name", "Real product", "priceVnd", 125000,
                "imageRefs", java.util.Map.of("main", "https://example.test/real.png"))));
        mvc.perform(get("/api/v1/referrals/store/shop-one").with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].storeId").value("shop-one"))
                .andExpect(jsonPath("$.result[0].productName").value("Real product"))
                .andExpect(jsonPath("$.result[0].productPrice").value(125000))
                .andExpect(jsonPath("$.result[0].productImageUrl").value("https://example.test/real.png"));
        mvc.perform(get("/api/v1/referrals/store/shop-one")).andExpect(status().isUnauthorized());
        when(access.storeProducts("another-shop")).thenThrow(new com.scanms.promotion.exception.AppException(com.scanms.promotion.exception.ErrorCode.FORBIDDEN));
        mvc.perform(get("/api/v1/referrals/store/another-shop").with(jwt())).andExpect(status().isForbidden());
        when(access.storeProducts("empty-shop")).thenReturn(java.util.List.of());
        mvc.perform(get("/api/v1/referrals/store/empty-shop").with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(0));
        when(access.storeProducts("shop-one")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(get("/api/v1/referrals/store/shop-one").with(jwt())).andExpect(status().isServiceUnavailable());
    }
}
