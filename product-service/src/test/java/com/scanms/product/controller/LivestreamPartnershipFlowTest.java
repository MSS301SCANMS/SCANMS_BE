package com.scanms.product.controller;

import com.scanms.product.constant.*;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.ProductAccess;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:live-partnership;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN"})
class LivestreamPartnershipFlowTest {
    @Autowired WebApplicationContext context;
    @Autowired LivestreamRepository lives;
    @Autowired ProductRepository products;
    @MockitoBean ProductAccess access;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    static volatile boolean partnership=true;
    static volatile java.util.concurrent.CountDownLatch concurrentResponses;
    static final HttpServer source=start();
    static HttpServer start(){try{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/referrals/livestream-access",exchange->{
            String user=exchange.getRequestURI().getQuery().contains("collaboratorId=kol-2")?"second-kol-user":"kol-user";
            var barrier=concurrentResponses;
            if(barrier!=null){barrier.countDown();try{barrier.await(400,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
            byte[] bytes=("{\"result\":{\"approvalStatus\":\"APPROVED\",\"userId\":\""+user+"\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(partnership?200:409,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.setExecutor(java.util.concurrent.ForkJoinPool.commonPool());server.start();return server;
    }catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void urls(DynamicPropertyRegistry registry){registry.add("clients.promotion.url",()->"http://127.0.0.1:"+source.getAddress().getPort());}
    @AfterAll static void stop(){source.stop(0);}
    @BeforeEach void setup(){
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        lives.deleteAll();partnership=true;concurrentResponses=null;
        when(access.userId()).thenReturn("shop-user");when(access.bearerToken()).thenReturn("fixture-token");
    }
    String create() throws Exception {
        mvc.perform(post("/api/v1/livestreams").with(jwt()).contentType("application/json").content("{\"storeId\":\"store-1\",\"title\":\"Fixture live\",\"collaboratorRefs\":{\"collaboratorIds\":[\"kol-1\"]},\"participantRefs\":{\"inviteStatus\":\"ACCEPTED\",\"collaboratorResponses\":{\"kol-1\":\"ACCEPTED\"}}}"))
                .andExpect(status().isCreated());
        return lives.findAll().getFirst().getLivestreamId();
    }
    @Test void shopCannotForgeConsentAndOnlyInvitedKolCanRespondThenStart() throws Exception {
        String id=create();
        mvc.perform(post("/api/v1/livestreams/"+id+"/start").with(jwt())).andExpect(status().isConflict());
        when(access.userId()).thenReturn("stranger");
        mvc.perform(patch("/api/v1/livestreams/"+id+"/respond").with(jwt()).contentType("application/json").content("{\"accepted\":true}")).andExpect(status().isForbidden());
        when(access.userId()).thenReturn("kol-user");
        mvc.perform(patch("/api/v1/livestreams/"+id+"/respond").with(jwt()).contentType("application/json").content("{\"accepted\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.participantRefs.inviteStatus").value("REJECTED"));
        mvc.perform(patch("/api/v1/livestreams/"+id+"/respond").with(jwt().jwt(jwt->jwt.subject("kol-subject"))).contentType("application/json").content("{\"accepted\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.participantRefs.respondedBySubject").value("kol-subject"));
        when(access.userId()).thenReturn("shop-user");
        mvc.perform(post("/api/v1/livestreams/"+id+"/start").with(jwt())).andExpect(status().isOk());
        assertEquals(LivestreamStatus.LIVE,lives.findById(id).orElseThrow().getStatus());
        mvc.perform(patch("/api/v1/livestreams/"+id).with(jwt()).contentType("application/json").content("{\"collaboratorRefs\":{\"collaboratorIds\":[\"other-kol\"]}}"))
                .andExpect(status().isConflict());
    }
    @Test void missingAcceptedPartnershipPreventsCreation() throws Exception {
        partnership=false;
        mvc.perform(post("/api/v1/livestreams").with(jwt()).contentType("application/json").content("{\"storeId\":\"store-1\",\"title\":\"Denied live\",\"collaboratorRefs\":{\"collaboratorIds\":[\"kol-1\"]}}"))
                .andExpect(status().isConflict());
        assertEquals(0,lives.count());
    }
    @Test void pastSchedulesAndInvalidEndTimesAreRejectedOnCreateAndUpdate() throws Exception {
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        var past = now.minusHours(1);
        var future = now.plusHours(1);
        for (String body : List.of(
                "{\"storeId\":\"store-1\",\"title\":\"Past\",\"scheduledAt\":\"" + past + "\"}",
                "{\"storeId\":\"store-1\",\"title\":\"Equal end\",\"scheduledAt\":\"" + future + "\",\"participantRefs\":{\"endsAt\":\"" + future.atOffset(java.time.ZoneOffset.UTC) + "\"}}",
                "{\"storeId\":\"store-1\",\"title\":\"Invalid end\",\"scheduledAt\":\"" + future + "\",\"participantRefs\":{\"endsAt\":\"not-a-date\"}}")) {
            mvc.perform(post("/api/v1/livestreams").with(jwt()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, lives.count());
        var end = future.plusHours(1).atOffset(java.time.ZoneOffset.UTC).withOffsetSameInstant(java.time.ZoneOffset.ofHours(7));
        mvc.perform(post("/api/v1/livestreams").with(jwt()).contentType("application/json").content(
                "{\"storeId\":\"store-1\",\"title\":\"Future\",\"scheduledAt\":\"" + future + "Z\",\"participantRefs\":{\"endsAt\":\"" + end + "\"}}"))
                .andExpect(status().isCreated());
        String id = lives.findAll().getFirst().getLivestreamId();
        mvc.perform(patch("/api/v1/livestreams/" + id).with(jwt()).contentType("application/json").content("{\"scheduledAt\":\"" + past + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/livestreams/" + id).with(jwt()).contentType("application/json").content("{\"participantRefs\":{\"endsAt\":\"" + future.minusMinutes(1).atOffset(java.time.ZoneOffset.UTC) + "\"}}"))
                .andExpect(status().isBadRequest());
        assertEquals(future, lives.findById(id).orElseThrow().getScheduledAt());
    }
    @Test void simultaneousKolResponsesRetainEveryDecision() throws Exception {
        mvc.perform(post("/api/v1/livestreams").with(jwt()).contentType("application/json").content("{\"storeId\":\"store-1\",\"title\":\"Two KOLs\",\"collaboratorRefs\":{\"collaboratorIds\":[\"kol-1\",\"kol-2\"]}}"))
                .andExpect(status().isCreated());
        String id=lives.findAll().getFirst().getLivestreamId();
        when(access.userId()).thenAnswer(invocation -> org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName());
        concurrentResponses=new java.util.concurrent.CountDownLatch(2);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var tasks=java.util.stream.Stream.of("kol-user","second-kol-user").map(subject -> pool.submit(() -> {
                start.await();
                mvc.perform(patch("/api/v1/livestreams/"+id+"/respond").with(jwt().jwt(token->token.subject(subject))).contentType("application/json").content("{\"accepted\":true}"))
                        .andExpect(status().isOk());
                return true;
            })).toList();
            start.countDown();
            for(var task:tasks) assertTrue(task.get(10,java.util.concurrent.TimeUnit.SECONDS));
        } finally {concurrentResponses=null;}
        var participants=lives.findById(id).orElseThrow().getParticipantRefs();
        assertEquals(Map.of("kol-1","ACCEPTED","kol-2","ACCEPTED"),participants.get("collaboratorResponses"));
        assertEquals("ACCEPTED",participants.get("inviteStatus"));
        mvc.perform(post("/api/v1/livestreams/"+id+"/start").with(jwt().jwt(token->token.subject("shop-user"))))
                .andExpect(status().isOk());
    }
}
