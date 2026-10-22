package com.scanms.product.controller;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Store;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.ProductAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:store-owner-details;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN"})
class StoreOwnerDetailsIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired StoreRepository stores;
    @MockitoBean ProductAccess access;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc; Store store; String endpoint;
    static final String NEW="100/31 Bình Giã, phường Tam Thắng, Thành phố Hồ Chí Minh";
    @BeforeEach void setup() {
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        stores.deleteAll();
        store=stores.saveAndFlush(Store.builder().name("Owner shop").ownerUserId("owner-1").approvalStatus(StoreStatus.APPROVED).build());
        endpoint="/api/v1/stores/"+store.getStoreId()+"/owner-details";
        when(access.requireOwnedStore(store.getStoreId())).thenAnswer(c->stores.findById(store.getStoreId()).orElseThrow());
        when(access.requireApprovedStore(store.getStoreId())).thenAnswer(c->stores.findById(store.getStoreId()).orElseThrow());
    }
    String body(String pickup,String returns) {
        return "{\"representativeName\":\"Owner\",\"businessType\":\"INDIVIDUAL\",\"taxCode\":\"TEST-ONLY\",\"contactPhone\":\"0901234567\",\"contactEmail\":\"owner@example.test\",\"businessAddress\":\"\",\"warehouseAddress\":\""+pickup+"\",\"returnWarehouseAddress\":\""+returns+"\"}";
    }
    @Test void newAddressPersistsAndIsExcludedFromPublicResponses() throws Exception {
        mvc.perform(put(endpoint).with(jwt()).contentType("application/json").content(body(NEW,NEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.warehouseAddress").value(NEW));
        assertEquals(NEW,stores.findById(store.getStoreId()).orElseThrow().getOwnerDetails().get("warehouseAddress"));
        mvc.perform(get(endpoint).with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.returnWarehouseAddress").value(NEW));
        mvc.perform(get("/api/v1/stores/"+store.getStoreId()).with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.ownerDetails").doesNotExist()).andExpect(jsonPath("$.result.onboardingData").doesNotExist());
        mvc.perform(get("/api/v1/stores/public/id/"+store.getStoreId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.ownerDetails").doesNotExist()).andExpect(jsonPath("$.result.contactPhone").doesNotExist());
    }
    @Test void legacyPickupAndNewReturnAddressWorkButIncompleteValuesCannotOverwriteThem() throws Exception {
        String old="100/31 Bình Giã, Phường 8, Thành phố Vũng Tàu, Bà Rịa - Vũng Tàu";
        mvc.perform(put(endpoint).with(jwt()).contentType("application/json").content(body(old,NEW))).andExpect(status().isOk());
        for(String invalid:new String[]{"100/31 Bình Giã, Thành phố Hồ Chí Minh","100/31 Bình Giã, , Thành phố Hồ Chí Minh","100/31 Bình Giã, phường Tam Thắng,"}) {
            mvc.perform(put(endpoint).with(jwt()).contentType("application/json").content(body(invalid,NEW))).andExpect(status().isBadRequest());
            mvc.perform(put(endpoint).with(jwt()).contentType("application/json").content(body(NEW,invalid))).andExpect(status().isBadRequest());
        }
        var saved=stores.findById(store.getStoreId()).orElseThrow().getOwnerDetails();
        assertEquals(old,saved.get("warehouseAddress"));assertEquals(NEW,saved.get("returnWarehouseAddress"));
    }
    @Test void anonymousAndOtherOwnersCannotReadOrModifyWarehouseDetails() throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(put(endpoint).contentType("application/json").content(body(NEW,NEW))).andExpect(status().isUnauthorized());
        when(access.requireOwnedStore(store.getStoreId())).thenThrow(new AppException(ErrorCode.FORBIDDEN));
        when(access.requireApprovedStore(store.getStoreId())).thenThrow(new AppException(ErrorCode.FORBIDDEN));
        mvc.perform(get(endpoint).with(jwt())).andExpect(status().isForbidden());
        mvc.perform(put(endpoint).with(jwt()).contentType("application/json").content(body(NEW,NEW))).andExpect(status().isForbidden());
        assertNull(stores.findById(store.getStoreId()).orElseThrow().getOwnerDetails());
    }
}
