package com.scanms.promotion.controller;

import com.scanms.promotion.repository.CommissionPolicyRepository;
import com.scanms.promotion.service.VoucherAccess;
import com.scanms.promotion.exception.*;
import org.junit.jupiter.api.*;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:policy-management;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class CommissionPolicyIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired CommissionPolicyRepository repository;
    @Autowired com.scanms.promotion.service.CommissionPolicyService service;
    @MockitoBean VoucherAccess access;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    String body = "{\"policyName\":\"Policy test\",\"scopeType\":\"PLATFORM\",\"commissionRate\":10,\"returnWindowDays\":14,\"validFrom\":\"2026-01-01T00:00:00Z\",\"status\":\"ACTIVE\",\"priority\":0}";
    @BeforeEach void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        repository.deleteAll();
    }
    @Test void createsListsUpdatesAndRejectsStaleVersion() throws Exception {
        mvc.perform(post("/api/v1/commission-policies").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.result.commissionRate").value(10));
        var saved = repository.findAll().getFirst();
        assertEquals(saved.getPolicyId(), service.resolvePolicy(null, null, null, null).getPolicyId());
        mvc.perform(get("/api/v1/commission-policies").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result[0].policyId").value(saved.getPolicyId()));
        String url = "/api/v1/commission-policies/" + saved.getPolicyId() + "?version=" + saved.getVersion();
        mvc.perform(put(url).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body.replace("ACTIVE", "INACTIVE").replace(":10,", ":12.5,")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("INACTIVE")).andExpect(jsonPath("$.result.commissionRate").value(12.5));
        mvc.perform(put(url).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertEquals("INACTIVE", repository.findById(saved.getPolicyId()).orElseThrow().getStatus().name());
        assertNull(service.resolvePolicy(null, null, null, null), "Disabled policy no longer participates in commission resolution");
        verify(access, times(3)).requireManager();
    }
    @Test void rejectsInvalidRatesDatesAndMissingScopeTargetWithoutSaving() throws Exception {
        for (String invalid : new String[]{body.replace(":10,", ":101,"), body.replace(":10,", ":-1,"), body.replace("PLATFORM", "STORE"), body.replace("PLATFORM", "PRODUCT"), body.replace("PLATFORM", "LIVESTREAM"), body.replace("PLATFORM", "COLLABORATOR"), body.replace("{", "{\"validTo\":\"2025-01-01T00:00:00Z\","), body.replace(":14,", ":-1,")})
            mvc.perform(post("/api/v1/commission-policies").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
    }
    @Test void requiresManagerForCreateAndUpdate() throws Exception {
        mvc.perform(post("/api/v1/commission-policies").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        doThrow(new AppException(ErrorCode.FORBIDDEN, "Manager required")).when(access).requireManager();
        mvc.perform(post("/api/v1/commission-policies").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/commission-policies/unknown?version=0").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertEquals(0, repository.count());
    }
    @Test void preservesStoreOwnerCreationButCannotSetPlatformPolicyOrEditAsOwner() throws Exception {
        String storeBody = body.replace("PLATFORM", "STORE").replace("{", "{\"storeId\":\"my-store\",");
        mvc.perform(post("/api/v1/commission-policies").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(storeBody)).andExpect(status().isCreated());
        verify(access).requireStoreOwner("my-store");
        doThrow(new AppException(ErrorCode.FORBIDDEN, "Store owner required")).when(access).requireStoreOwner("another-store");
        mvc.perform(post("/api/v1/commission-policies").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(storeBody.replace("my-store", "another-store"))).andExpect(status().isForbidden());
        assertEquals(1, repository.count());
    }
}
