package com.scanms.payment.ops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra endpoint scanmsinfo/scanmslogs của scanms-ops-starter chạy thật trong một service: bảo mật, đọc
 * role từ claim Keycloak, che dữ liệu nhạy cảm, và chỉ mở đúng các endpoint cần thiết.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ops-endpoints;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.payment.bank-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "logging.level.root=WARN"})
class OpsEndpointsIntegrationTest {
    @Autowired WebApplicationContext context;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor withRoles(String... roles) {
        return jwt().jwt(j -> j.claim("realm_access", Map.of("roles", List.of(roles))));
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mvc.perform(get("/actuator/scanmsinfo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/scanmslogs")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminRolesAreForbidden() throws Exception {
        mvc.perform(get("/actuator/scanmsinfo").with(withRoles("USER", "MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/scanmslogs").with(withRoles("SHOP_OWNER"))).andExpect(status().isForbidden());
    }

    @Test
    void adminSeesProcessInfoAndDatabaseStatus() throws Exception {
        mvc.perform(get("/actuator/scanmsinfo").with(withRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-service"))
                .andExpect(jsonPath("$.process.pid").isNumber())
                .andExpect(jsonPath("$.process.javaVersion").isString())
                .andExpect(jsonPath("$.memory.heapUsedBytes").isNumber())
                .andExpect(jsonPath("$.dependencies[*].name", hasItem("PostgreSQL")))
                .andExpect(jsonPath("$.dependencies[?(@.name=='PostgreSQL')].status", hasItem("UP")));
        mvc.perform(get("/actuator/scanmsinfo").with(withRoles("SYSTEM_ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void adminSeesRecentLogsAndErrorsWithMasking() throws Exception {
        var log = LoggerFactory.getLogger("com.scanms.payment.OpsMarker");
        log.warn("ops-marker-warn password=hunter2 email a.b@example.com");
        log.error("ops-marker-error", new IllegalStateException("boom"));
        mvc.perform(get("/actuator/scanmslogs?level=WARN").with(withRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[*].message", hasItem(containsString("ops-marker-warn password=***"))))
                .andExpect(jsonPath("$.entries[*].message", hasItem(containsString("a***@example.com"))))
                .andExpect(jsonPath("$.errors[0].stackTrace", containsString("IllegalStateException: boom")));
        mvc.perform(get("/actuator/scanmslogs?level=LOUD").with(withRoles("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void healthStaysPublicButHidesDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/loggers")) {
            var response = mvc.perform(get(path).with(withRoles("ADMIN"))).andReturn().getResponse();
            org.junit.jupiter.api.Assertions.assertNotEquals(200, response.getStatus(), path + " không được lộ ra");
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asRole(String role) {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void paymentOverviewIsAdminOnlyAndReportsOnlyFlagsAndCounts() throws Exception {
        mvc.perform(get("/api/v1/finance/system/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/finance/system/overview").with(asRole("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/finance/system/overview").with(asRole("USER"))).andExpect(status().isForbidden());
        for (String role : List.of("ADMIN", "SYSTEM_ADMIN")) {
            mvc.perform(get("/api/v1/finance/system/overview").with(asRole(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.config.automaticPayouts").isBoolean())
                    .andExpect(jsonPath("$.result.config.bankEncryptionKeyConfigured").value(true))
                    .andExpect(jsonPath("$.result.config.minimumWithdrawalVnd").isNumber())
                    .andExpect(jsonPath("$.result.financeEvents.pending").isNumber())
                    .andExpect(jsonPath("$.result.paymentsByStatus").isArray())
                    .andExpect(jsonPath("$.result.withdrawalsByStatus").isArray())
                    .andExpect(jsonPath("$.result.walletsByOwnerType").isArray())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(org.hamcrest.Matchers.not(containsString("AAAAAAAAAAAA"))));
        }
    }

    @Test
    void kafkaStatusExcludesManagers() throws Exception {
        mvc.perform(get("/api/v1/finance/system/kafka").with(asRole("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/finance/system/kafka").with(asRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.up").isBoolean());
    }
}
