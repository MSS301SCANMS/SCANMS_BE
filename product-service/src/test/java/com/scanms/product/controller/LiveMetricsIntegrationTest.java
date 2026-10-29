package com.scanms.product.controller;

import com.scanms.product.entity.Livestream;
import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.ProductAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:live-metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.catalog.seed-enabled=false", "scanms.catalog.cache-enabled=false", "scanms.live.metrics-secret=fixture-metrics-key-32-characters-long", "logging.level.root=WARN", "debug=false"})
class LiveMetricsIntegrationTest {
    static final String KEY = "fixture-metrics-key-32-characters-long";
    @Autowired
    WebApplicationContext context;
    @Autowired
    LivestreamRepository lives;
    @MockitoBean
    ProductAccess access;
    @MockitoBean
    JwtDecoder decoder;
    MockMvc mvc;
    String id;

    @BeforeEach
    void setup() {
        lives.deleteAll();
        id = lives.saveAndFlush(Livestream.builder().storeId("store").title("Live").status(LivestreamStatus.LIVE).viewerCount(0L).uniqueViewerCount(0L).peakConcurrentViewerCount(0L).build()).getLivestreamId();
        when(access.userId()).thenReturn("owner");
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String signature(String payload, String time) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((payload + "\n" + time).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    void visit(String user, String event) throws Exception {
        String time = "" + Instant.now().getEpochSecond();
        mvc.perform(post("/api/v1/livestreams/" + id + "/views").with(jwt().jwt(j -> j.subject(user))).header("X-Live-Metrics-Time", time).header("X-Live-Metrics-Signature", signature("POST\n" + id + "\n" + event, time)).contentType("application/json").content("{\"visitId\":\"" + event + "\"}")).andExpect(status().isOk());
    }

    @Test
    void retryAndReconnectKeepUniqueViewersDurablyAndHideIdentityHashes() throws Exception {
        visit("viewer-a", "connection-one");
        visit("viewer-a", "connection-one");
        visit("viewer-a", "connection-two");
        visit("viewer-b", "connection-three");
        var live = lives.findById(id).orElseThrow();
        assertEquals(3L, live.getViewerCount());
        assertEquals(2L, live.getUniqueViewerCount());
        String time = "" + Instant.now().getEpochSecond();
        mvc.perform(patch("/api/v1/livestreams/" + id + "/stats").with(jwt()).header("X-Live-Metrics-Time", time).header("X-Live-Metrics-Signature", signature("PATCH\n" + id + "\n0\n0\n2", time)).contentType("application/json").content("{\"viewerCount\":0,\"uniqueViewerCount\":0,\"concurrentViewerCount\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.result.peakConcurrentViewerCount").value(2));
        var json = mvc.perform(get("/api/v1/livestreams/" + id).with(jwt())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("statistics"));
        assertFalse(json.contains("viewer-a"));
        assertEquals(2L, lives.findById(id).orElseThrow().getUniqueViewerCount());
    }

    @Test
    void customersCannotForgeMetricsWithoutRelaySignature() throws Exception {
        mvc.perform(post("/api/v1/livestreams/" + id + "/views").with(jwt()).contentType("application/json").content("{\"visitId\":\"forged\"}")).andExpect(status().isForbidden());
        assertEquals(0L, lives.findById(id).orElseThrow().getViewerCount());
    }

    @Test
    void sharedHeartsPersistMonotonicallyAndExposeOnlyTheTotal() throws Exception {
        visit("viewer-a", "view-before-heart");
        for (long count : new long[]{17, 17, 5}) {
            String time = "" + Instant.now().getEpochSecond();
            mvc.perform(patch("/api/v1/livestreams/" + id + "/reactions/stats").with(jwt())
                            .header("X-Live-Metrics-Time", time).header("X-Live-Metrics-Signature", signature("LIKES\n" + id + "\n" + count, time))
                            .contentType("application/json").content("{\"likeCount\":" + count + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result.likeCount").value(17));
        }
        mvc.perform(get("/api/v1/livestreams/" + id).with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.likeCount").value(17)).andExpect(jsonPath("$.result.statistics").doesNotExist());
        assertEquals(1L, lives.findById(id).orElseThrow().getUniqueViewerCount());
    }

    @Test
    void browserCannotForgeAHeartTotalOrReuseASignatureForAnotherCount() throws Exception {
        mvc.perform(patch("/api/v1/livestreams/" + id + "/reactions/stats").with(jwt()).contentType("application/json").content("{\"likeCount\":9999}"))
                .andExpect(status().isForbidden());
        String time = "" + Instant.now().getEpochSecond();
        mvc.perform(patch("/api/v1/livestreams/" + id + "/reactions/stats").with(jwt())
                        .header("X-Live-Metrics-Time", time).header("X-Live-Metrics-Signature", signature("LIKES\n" + id + "\n1", time))
                        .contentType("application/json").content("{\"likeCount\":9999}"))
                .andExpect(status().isForbidden());
        assertFalse(lives.findById(id).orElseThrow().getStatistics() != null && lives.findById(id).orElseThrow().getStatistics().containsKey("likeCount"));
    }
}
