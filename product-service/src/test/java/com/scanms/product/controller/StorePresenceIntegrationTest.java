package com.scanms.product.controller;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.ProductAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:store-presence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN"})
class StorePresenceIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired StoreRepository stores;
    @MockitoBean ProductAccess access;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc; Store own,other;
    @BeforeEach void setup(){
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();stores.deleteAll();
        own=stores.saveAndFlush(Store.builder().ownerUserId("owner-1").name("Own shop").approvalStatus(StoreStatus.APPROVED).build());
        other=stores.saveAndFlush(Store.builder().ownerUserId("owner-2").name("Other shop").approvalStatus(StoreStatus.APPROVED).build());when(access.userId()).thenReturn("owner-1");
    }
    @Test void ownerHeartbeatPersistsOnlyOwnStoreWithoutChangingSettingsVersion() throws Exception {
        var before=stores.findById(own.getStoreId()).orElseThrow();var version=before.getVersion();var updated=before.getUpdatedAt();
        mvc.perform(post("/api/v1/stores/my-store/presence").with(jwt()).contentType("application/json").content("{\"storeId\":\""+other.getStoreId()+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result[0].storeId").value(own.getStoreId())).andExpect(jsonPath("$.result[0].isOnline").value(true));
        var saved=stores.findById(own.getStoreId()).orElseThrow();assertNotNull(saved.getLastActiveAt());assertEquals(version,saved.getVersion());assertEquals(updated,saved.getUpdatedAt());
        assertNull(stores.findById(other.getStoreId()).orElseThrow().getLastActiveAt());
        mvc.perform(get("/api/v1/stores/public/id/"+own.getStoreId()+"/presence"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.isOnline").value(true)).andExpect(jsonPath("$.result.ownerUserId").doesNotExist());
    }
    @Test void publicReadsNeverMarkShopOnlineAndOldHeartbeatBecomesOffline() throws Exception {
        mvc.perform(get("/api/v1/stores/public/id/"+own.getStoreId()+"/presence"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.isOnline").value(false)).andExpect(jsonPath("$.result.lastActiveAt").isEmpty());
        own.setLastActiveAt(Instant.now().minusSeconds(120));stores.saveAndFlush(own);
        mvc.perform(get("/api/v1/stores/public/id/"+own.getStoreId()+"/presence"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.isOnline").value(false));
        assertTrue(stores.findById(own.getStoreId()).orElseThrow().getLastActiveAt().isBefore(Instant.now().minusSeconds(100)));
    }
    @Test void authenticationApprovedStoreAndMissingApiAreHandledCorrectly() throws Exception {
        mvc.perform(post("/api/v1/stores/my-store/presence")).andExpect(status().isUnauthorized());
        when(access.userId()).thenReturn("no-store-owner");mvc.perform(post("/api/v1/stores/my-store/presence").with(jwt())).andExpect(status().isConflict());
        own.setApprovalStatus(StoreStatus.PENDING);stores.saveAndFlush(own);when(access.userId()).thenReturn("owner-1");
        mvc.perform(post("/api/v1/stores/my-store/presence").with(jwt())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/stores/public/id/"+own.getStoreId()+"/presence")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/stores/nonexistent/path").with(jwt())).andExpect(status().isNotFound());
    }
}
