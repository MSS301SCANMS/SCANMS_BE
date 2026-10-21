package com.scanms.promotion.controller;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.entity.CollaboratorProfile;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.service.VoucherAccess;
import com.scanms.promotion.service.KolRoleGranter;
import com.scanms.promotion.mapper.CollaboratorProfileMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:kol-application;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class KolApplicationIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired CollaboratorProfileRepository profiles;
    @Autowired CollaboratorProfileMapper mapper;
    @MockitoBean VoucherAccess access;
    @MockitoBean KolRoleGranter roleGranter;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    String body="{\"userId\":\"forged-user\",\"approvalStatus\":\"APPROVED\",\"idCardNumber\":\"000000000001\",\"bankName\":\"Test Bank\",\"bankAccountNumber\":\"000001\",\"bankAccountName\":\"TEST USER\",\"platform\":\"TIKTOK\",\"channelName\":\"Test channel\",\"channelUrl\":\"https://example.test/channel\",\"followerCount\":15000,\"frontCardUrl\":\"https://example.test/front.png\",\"backCardUrl\":\"https://example.test/back.png\",\"channelProofUrl\":\"https://example.test/channel.png\"}";
    @BeforeEach void setup() {
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        profiles.deleteAll(); when(access.userId()).thenReturn("current-user"); when(access.isManager()).thenReturn(false);
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor customer() {return jwt().jwt(j->j.claim("realm_access",Map.of("roles",List.of("USER"))));}
    org.springframework.test.web.servlet.request.RequestPostProcessor manager() {return jwt().jwt(j->j.claim("realm_access",Map.of("roles",List.of("MANAGER"))));}
    @Test void savesAllDetailsReadsThemBackAndUpdatesSamePendingProfile() throws Exception {
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.userId").value("current-user"))
            .andExpect(jsonPath("$.result.approvalStatus").value("PENDING"))
            .andExpect(jsonPath("$.result.applicationData.bankAccountNumber").value("000001"));
        assertEquals(1,profiles.count());
        mvc.perform(get("/api/v1/collaborators/me").with(customer())).andExpect(status().isOk())
            .andExpect(jsonPath("$.result.applicationData.frontCardUrl").value("https://example.test/front.png"))
            .andExpect(jsonPath("$.result.applicationData.channelName").value("Test channel"));
        var id=profiles.findAll().getFirst().getCollaboratorId();
        mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body.replace("000001\"","000002\""))).andExpect(status().isOk()).andExpect(jsonPath("$.result.collaboratorId").value(id));
        when(access.isManager()).thenReturn(true);
        mvc.perform(get("/api/v1/collaborators").with(manager())).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].applicationData.bankName").value("Test Bank"));
        mvc.perform(patch("/api/v1/collaborators/"+id+"/approve").with(manager())).andExpect(status().isOk());
        verify(roleGranter).grant("current-user");
        mvc.perform(patch("/api/v1/collaborators/"+id+"/approve").with(manager())).andExpect(status().isOk());
        verify(roleGranter, times(2)).grant("current-user");
        mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }
    @Test void legacyPendingCanBeSupplementedButCannotBeApprovedEmpty() throws Exception {
        var legacy=profiles.saveAndFlush(CollaboratorProfile.builder().userId("current-user").approvalStatus(CollaboratorStatus.PENDING).build());
        mvc.perform(patch("/api/v1/collaborators/"+legacy.getCollaboratorId()+"/approve").with(manager())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.result.collaboratorId").value(legacy.getCollaboratorId()));
        assertEquals(1,profiles.count());
    }
    @Test void nonOwnerCannotReadDocumentsAndNonManagerListIsRedacted() throws Exception {
        mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        String id=profiles.findAll().getFirst().getCollaboratorId();
        when(access.userId()).thenReturn("another-user");
        mvc.perform(get("/api/v1/collaborators/"+id).with(customer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/collaborators").with(customer())).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].applicationData").isEmpty());
        mvc.perform(patch("/api/v1/collaborators/"+id+"/approve").with(customer())).andExpect(status().isForbidden());
    }
    @Test void rejectsMissingFieldsBadUrlsAndForgedIdentityOnLegacyRoute() throws Exception {
        mvc.perform(post("/api/v1/collaborators/application").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for(String bad:List.of("{}",body.replace("000000000001","bad"),body.replace("15000","-1"),body.replace("https://example.test/front.png","javascript:alert(1)")))
            mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/collaborators").with(customer()).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"another-user\"}")).andExpect(status().isForbidden());
        assertEquals(0,profiles.count());
    }
    @Test void rejectedProfileRetainsReasonAndCanResubmitDetailsWithoutDuplicate() throws Exception {
        mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        String id=profiles.findAll().getFirst().getCollaboratorId();
        mvc.perform(patch("/api/v1/collaborators/"+id+"/reject").with(manager()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Image needs updating\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.result.rejectionReason").value("Image needs updating"));
        mvc.perform(post("/api/v1/collaborators/application").with(customer()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.result.approvalStatus").value("PENDING"));
        assertEquals(1,profiles.count());
    }
}
