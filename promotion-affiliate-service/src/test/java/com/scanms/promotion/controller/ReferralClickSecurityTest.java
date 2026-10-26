package com.scanms.promotion.controller;

import com.scanms.promotion.service.ShortVideoReferralService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:referral-click-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN","debug=false"})
class ReferralClickSecurityTest {
    @Autowired WebApplicationContext context;
    @MockitoBean ShortVideoReferralService service;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void permitsAnonymousClicksButRestrictsVerificationToOrderInternal() throws Exception {
        when(service.click(any())).thenReturn(Map.of("clickId","click","expiresAt","2026-10-27T00:00:00Z"));
        mvc.perform(post("/api/v1/referrals/clicks").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":\"product\",\"referralLinkId\":\"ref\",\"shortVideoId\":\"video\"}")).andExpect(status().isOk());
        String path="/api/v1/referrals/clicks/click/verify?productId=product&customerId=customer";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().jwt(j->j.claim("realm_access",Map.of("roles",List.of("CUSTOMER")))))).andExpect(status().isForbidden());
        when(service.verifyClick("click","product","customer")).thenReturn(Map.of("referralLinkId","ref"));
        mvc.perform(get(path).with(jwt().jwt(j->j.claim("realm_access",Map.of("roles",List.of("ORDER_INTERNAL")))))).andExpect(status().isOk()).andExpect(jsonPath("$.result.referralLinkId").value("ref"));
        verify(service,times(1)).verifyClick("click","product","customer");
    }
}
