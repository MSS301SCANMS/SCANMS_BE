package com.scanms.promotion.controller;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.entity.CollaboratorProfile;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:partnership;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN","scanms.finance.client-secret="})
class PartnershipInvitationIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired PartnershipInvitationRepository invitations;
    @Autowired CollaboratorProfileRepository profiles;
    @Autowired ReferralLinkRepository links;
    @Autowired FinanceContractController financeContracts;
    @MockitoBean VoucherAccess access;
    @MockitoBean CollaborationAccess catalog;
    @MockitoBean ReferralProductAccess products;
    @MockitoBean LivestreamAttributionSource livestreams;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;String kolId;
    @BeforeEach void setup() {
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        invitations.deleteAll();links.deleteAll();profiles.deleteAll();
        kolId=profiles.saveAndFlush(CollaboratorProfile.builder().userId("kol-user").approvalStatus(CollaboratorStatus.APPROVED)
            .applicationData(Map.of("channelName","Creator One","platform","TIKTOK","followerCount",12500,"idCardNumber","PRIVATE","bankAccountNumber","PRIVATE","frontCardUrl","https://private.test/id.png")).build()).getCollaboratorId();
        when(access.userId()).thenReturn("shop-user");
        when(catalog.stores(any(),eq(false))).thenReturn(List.of(Map.of("storeId","store-1","name","Store One","approvalStatus","APPROVED")));
        when(access.storeProducts("store-1")).thenReturn(List.of(Map.of("productId","product-1","name","Product One","status","ACTIVE")));
    }
    String body(){return "{\"storeId\":\"store-1\",\"collaboratorId\":\""+kolId+"\",\"productId\":\"product-1\",\"message\":\"Let us promote this product together.\"}";}
    String create() throws Exception {
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("PENDING"));
        return invitations.findAll().getFirst().getId();
    }
    @Test void sourceDispatchersCanCalculateButCannotAcknowledgeWalletCredits() {
        for(String role:List.of("ORDER_INTERNAL","AFFILIATE_INTERNAL")) {
            var token=Jwt.withTokenValue("source-token").header("alg","RS256").subject("source").claim("realm_access",Map.of("roles",List.of(role))).build();
            assertDoesNotThrow(()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(financeContracts,"trusted",token,true));
            assertThrows(ResponseStatusException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(financeContracts,"trusted",token,false));
        }
    }
    @Test void livePurchasesAttributeOnlyTheAcceptedSingleHostAndReuseTheSameLink() throws Exception {
        String id=create();when(access.userId()).thenReturn("kol-user");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}")).andExpect(status().isOk());
        var live=new HashMap<String,Object>(Map.of("storeId","store-1","status","LIVE","productRefs",Map.of("productIds",List.of("product-1")),
            "collaboratorRefs",Map.of("collaboratorIds",List.of(kolId)),"participantRefs",Map.of("collaboratorResponses",Map.of(kolId,"ACCEPTED"))));
        when(livestreams.session(eq("live-1"),anyString())).thenAnswer(call->live);
        var internal=jwt().jwt(token->token.claim("realm_access",Map.of("roles",List.of("ORDER_INTERNAL"))));
        String path="/api/v1/referrals/livestream-attribution/live-1";
        mvc.perform(get(path).with(jwt()).param("productId","product-1")).andExpect(status().isForbidden());
        for(int i=0;i<2;i++)mvc.perform(get(path).with(internal).param("productId","product-1")).andExpect(status().isOk()).andExpect(jsonPath("$.result.collaboratorId").value(kolId));
        assertEquals(1,links.findAll().stream().filter(link->"live-1".equals(link.getLivestreamId())).count());
        mvc.perform(get(path).with(internal).param("productId","wrong-product")).andExpect(status().isConflict());
        live.put("participantRefs",Map.of("collaboratorResponses",Map.of(kolId,"PENDING")));
        mvc.perform(get(path).with(internal).param("productId","product-1")).andExpect(status().isConflict());
        live.put("status","ENDED");
        mvc.perform(get(path).with(internal).param("productId","product-1")).andExpect(status().isConflict());
    }
    @Test void livestreamAccessRequiresAcceptedPartnershipAndExposesNoPrivateDocuments() throws Exception {
        String id=create();
        mvc.perform(get("/api/v1/referrals/livestream-access").with(jwt()).param("storeId","store-1").param("collaboratorId",kolId)).andExpect(status().isConflict());
        when(access.userId()).thenReturn("kol-user");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}")).andExpect(status().isOk());
        when(access.userId()).thenReturn("shop-user");
        mvc.perform(get("/api/v1/referrals/livestream-access").with(jwt()).param("storeId","store-1").param("collaboratorId",kolId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.userId").value("kol-user"))
            .andExpect(jsonPath("$.result.frontCardUrl").doesNotExist()).andExpect(jsonPath("$.result.bankAccountNumber").doesNotExist());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireStoreOwner("store-1");
        when(access.userId()).thenReturn("stranger");
        mvc.perform(get("/api/v1/referrals/livestream-access").with(jwt()).param("storeId","store-1").param("collaboratorId",kolId)).andExpect(status().isForbidden());
    }
    @Test void checkoutAttributionIsInternalAndBoundToAnActiveLinkAndItsProduct() throws Exception {
        String id=create();when(access.userId()).thenReturn("kol-user");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}")).andExpect(status().isOk());
        String linkId=links.findAll().getFirst().getReferralLinkId();
        mvc.perform(get("/api/v1/referrals/attribution/"+linkId).with(jwt()).param("productId","product-1")).andExpect(status().isForbidden());
        var internal=jwt().jwt(token->token.claim("realm_access",Map.of("roles",List.of("ORDER_INTERNAL"))));
        mvc.perform(get("/api/v1/referrals/attribution/"+linkId).with(internal).param("productId","product-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.collaboratorId").value(kolId));
        mvc.perform(get("/api/v1/referrals/attribution/"+linkId).with(internal).param("productId","wrong-product")).andExpect(status().isConflict());
        var profile=profiles.findById(kolId).orElseThrow();profile.setApprovalStatus(CollaboratorStatus.SUSPENDED);profiles.saveAndFlush(profile);
        mvc.perform(get("/api/v1/referrals/attribution/"+linkId).with(internal).param("productId","product-1")).andExpect(status().isConflict());
    }
    @Test void persistsAndReadsForBothOwnersWithoutLeakingKyc() throws Exception {
        String id=create();assertEquals(1,invitations.count());
        mvc.perform(get("/api/v1/referrals/invitations/shop").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].id").value(id));
        mvc.perform(get("/api/v1/referrals/invitations/catalog").with(jwt())).andExpect(status().isOk())
            .andExpect(jsonPath("$.result.creators[0].name").value("Creator One"))
            .andExpect(jsonPath("$.result.creators[0].idCardNumber").doesNotExist())
            .andExpect(jsonPath("$.result.creators[0].bankAccountNumber").doesNotExist())
            .andExpect(jsonPath("$.result.creators[0].frontCardUrl").doesNotExist());
        when(access.userId()).thenReturn("kol-user");
        mvc.perform(get("/api/v1/referrals/invitations/mine").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].productName").value("Product One"));
        when(access.userId()).thenReturn("different-shop");when(catalog.stores(any(),eq(false))).thenReturn(List.of());
        mvc.perform(get("/api/v1/referrals/invitations/shop").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    }
    @Test void acceptanceCreatesOneRealReferralAndRetryIsIdempotent() throws Exception {
        String id=create();when(access.userId()).thenReturn("kol-user");
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.result.referralToken").value("invite-"+id));
        assertEquals(1,links.count());assertEquals(kolId,links.findAll().getFirst().getCollaboratorId());
        assertEquals("product-1",links.findAll().getFirst().getProductId());
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}"))
            .andExpect(status().isConflict());
    }
    @Test void rejectsWrongOwnerDuplicateAndUnapprovedCreator() throws Exception {
        create();mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isConflict());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireStoreOwner("other-store");
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body().replace("store-1","other-store"))).andExpect(status().isForbidden());
        var kol=profiles.findById(kolId).orElseThrow();kol.setApprovalStatus(CollaboratorStatus.PENDING);profiles.saveAndFlush(kol);
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isConflict());
        assertEquals(1,invitations.count());
    }
    @Test void wrongKolCannotRespondAndShopCanWithdrawPendingOnly() throws Exception {
        String id=create();profiles.saveAndFlush(CollaboratorProfile.builder().userId("other-kol").approvalStatus(CollaboratorStatus.APPROVED).build());
        when(access.userId()).thenReturn("other-kol");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}")).andExpect(status().isForbidden());
        when(access.userId()).thenReturn("shop-user");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/cancel").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CANCELLED"));
        when(access.userId()).thenReturn("kol-user");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}")).andExpect(status().isConflict());assertEquals(0,links.count());
        when(access.userId()).thenReturn("shop-user");create();assertEquals(2,invitations.count(),"A withdrawn invitation can be resent while retaining its history");
    }
    @Test void unavailableProductRollsBackAcceptanceAndRejectionCreatesNoLink() throws Exception {
        String id=create();when(access.userId()).thenReturn("kol-user");
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Product unavailable")).when(products).requireAvailable("product-1");
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}")).andExpect(status().isConflict());
        assertEquals("PENDING",invitations.findById(id).orElseThrow().getStatus());assertEquals(0,links.count());
        mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("REJECTED"));assertEquals(0,links.count());
    }
    @Test void requiresAuthenticationAndMessageLimitAndActiveOwnedProduct() throws Exception {
        mvc.perform(get("/api/v1/referrals/invitations/catalog")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body().replace("Let us promote this product together.","x".repeat(2001)))).andExpect(status().isBadRequest());
        when(access.storeProducts("store-1")).thenReturn(List.of(Map.of("productId","product-1","name","Product One","status","INACTIVE")));
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isConflict());assertEquals(0,invitations.count());
    }
    @Test void generalInvitationWorksWithoutProductsAndAcceptanceDoesNotCreateReferral() throws Exception {
        when(access.storeProducts(anyString())).thenThrow(new IllegalStateException("General introductions must not request products"));
        String general=body().replace("\"productId\":\"product-1\",","").replace(",\"message\":\"Let us promote this product together.\"","");
        mvc.perform(get("/api/v1/referrals/invitations/catalog").with(jwt())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(general))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("PENDING")).andExpect(jsonPath("$.result.productId").isEmpty())
            .andExpect(jsonPath("$.result.message").value("Chào bạn, shop mong muốn kết nối và trao đổi cơ hội hợp tác cùng bạn."));
        String id=invitations.findAll().getFirst().getId();assertNull(invitations.findById(id).orElseThrow().getProductId());
        mvc.perform(post("/api/v1/referrals/invitations").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(general)).andExpect(status().isConflict());
        when(access.userId()).thenReturn("kol-user");
        mvc.perform(get("/api/v1/referrals/invitations/mine").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].id").value(id));
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/referrals/invitations/"+id+"/respond").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("ACCEPTED")).andExpect(jsonPath("$.result.referralLinkId").isEmpty());
        assertEquals("ACCEPTED",invitations.findById(id).orElseThrow().getStatus());assertEquals(0,links.count());
        verify(access,never()).storeProducts(anyString());
    }
}
