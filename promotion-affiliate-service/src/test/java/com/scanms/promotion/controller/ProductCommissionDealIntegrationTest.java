package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.request.CommissionNegotiationRequest;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:product-commission-deals;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN","scanms.finance.client-secret="})
class ProductCommissionDealIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void postgres(org.springframework.test.context.DynamicPropertyRegistry r) {
        String prefix=System.getenv("SCANMS_TEST_POSTGRES_PREFIX");
        if(prefix==null || prefix.isBlank()) return;
        if(!prefix.startsWith("jdbc:postgresql://127.0.0.1:") || !prefix.endsWith("/scanms_qa_")) throw new IllegalArgumentException("Use an isolated QA database");
        r.add("spring.datasource.url",()->prefix+"promotion");
        r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        r.add("spring.datasource.username",()->System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_USER","postgres"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("SCANMS_TEST_POSTGRES_PASSWORD",""));
    }
    @Autowired ProductCommissionDealService service;
    @Autowired ProductCommissionDealRepository deals;
    @Autowired CollaboratorProfileRepository profiles;
    @Autowired CommissionPolicyRepository policies;
    @Autowired WebApplicationContext context;
    @Autowired DefaultCommissionPolicySeed defaultSeed;
    @MockitoBean VoucherAccess access;
    @MockitoBean ReferralProductAccess catalog;
    @MockitoBean CollaborationAccess collaboration;
    @MockitoBean JwtDecoder decoder;
    String kol; CommissionPolicy base;
    @BeforeEach void setup() {
        deals.deleteAll(); profiles.deleteAll(); policies.deleteAll();
        kol=profiles.saveAndFlush(CollaboratorProfile.builder().userId("kol-user").approvalStatus(CollaboratorStatus.APPROVED).applicationData(Map.of("channelName","Creator" )).build()).getCollaboratorId();
        base=policies.saveAndFlush(CommissionPolicy.builder().policyName("Default 10%").scopeType(CommissionPolicyScope.PLATFORM).commissionRate(BigDecimal.TEN).returnWindowDays(14).validFrom(Instant.now().minusSeconds(60)).status(CommissionPolicyStatus.ACTIVE).priority(0).build());
        when(access.userId()).thenReturn("kol-user");
        doReturn(Map.of("id","shirt","title","Sora shirt","storeId","shop","imageUrl","/shirt.webp","store",Map.of("name","Sora Skin"))).when(catalog).product("shirt");
        when(collaboration.stores(any(),eq(false))).thenReturn(List.of(Map.of("storeId","shop")));
    }
    ProductCommissionDeal shopRespond(String id, String decision, String note) {
        when(access.userId()).thenReturn("shop-owner");
        try { return service.respond(id, decision, note); }
        finally { when(access.userId()).thenReturn("kol-user"); }
    }
    ProductCommissionDeal propose(int points) { return service.propose("shirt", points, "Promote this shirt on my channel"); }

    @Test void pendingDoesNotApplyAndOnlyOneTwoThreePointsCanBeRequested() {
        for(int invalid : List.of(-1,0,4,10)) assertEquals(400,assertThrows(ResponseStatusException.class,()->propose(invalid)).getStatusCode().value());
        var pending=propose(2);
        assertEquals("PENDING",pending.getStatus()); assertNull(pending.getTotalRate());
        assertEquals(0,service.quote("shirt").extraPoints());
        assertEquals(0,service.rate(kol,"shirt","shop",Instant.now(),base).totalRate().compareTo(BigDecimal.TEN));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->propose(3)).getStatusCode().value());
    }

    @Test void shopCounterKolAcceptanceRejectsStaleVersionsAndSelfAcceptance() {
        var pending = propose(3);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.negotiate(pending.getId(),
                new CommissionNegotiationRequest("ACCEPT", pending.getVersion(), null, "Self"))).getStatusCode().value());
        when(access.userId()).thenReturn("shop-owner");
        var counter = service.negotiate(pending.getId(), new CommissionNegotiationRequest("COUNTER", pending.getVersion(), 2, "Shop proposes 12%"));
        assertEquals("COUNTERED", counter.getStatus());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.negotiate(counter.getId(),
                new CommissionNegotiationRequest("ACCEPT", counter.getVersion(), null, "Self"))).getStatusCode().value());
        when(access.userId()).thenReturn("kol-user");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.negotiate(counter.getId(),
                new CommissionNegotiationRequest("ACCEPT", pending.getVersion(), null, "Old version"))).getStatusCode().value());
        var approved = service.negotiate(counter.getId(), new CommissionNegotiationRequest("ACCEPT", counter.getVersion(), null, "Agreed"));
        assertEquals("APPROVED", approved.getStatus());
        assertEquals(0, BigDecimal.valueOf(12).compareTo(approved.getTotalRate()));
        assertEquals(3, approved.getNegotiationHistory().size());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.negotiate(counter.getId(),
                new CommissionNegotiationRequest("ACCEPT", counter.getVersion(), null, "Replay"))).getStatusCode().value());
        assertEquals(0, service.quote("shirt").totalRate().compareTo(BigDecimal.valueOf(12)));
    }

    @Test void simultaneousAcceptanceUsesExactlyOneVersion() throws Exception {
        var pending = propose(2);
        when(access.userId()).thenReturn("shop-owner");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                gate.await();
                try {
                    service.negotiate(pending.getId(), new CommissionNegotiationRequest("ACCEPT", pending.getVersion(), null, "Accept concurrently"));
                    return true;
                } catch (ResponseStatusException ex) {
                    assertEquals(409, ex.getStatusCode().value());
                    return false;
                }
            }));
            gate.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
            assertEquals("APPROVED", deals.findById(pending.getId()).orElseThrow().getStatus());
        } finally { when(access.userId()).thenReturn("kol-user"); }
    }

    @Test void approvedOneTwoThreePointsProduceElevenTwelveThirteenWithoutStacking() {
        String previous=null;
        for(int extra : List.of(1,2,3)) {
            var d=propose(extra);
            var approved=shopRespond(d.getId(),"APPROVED","Approved");
            assertEquals(0,approved.getTotalRate().compareTo(BigDecimal.valueOf(10+extra)));
            assertEquals(0,service.quote("shirt").totalRate().compareTo(BigDecimal.valueOf(10+extra)));
            assertEquals(extra,service.quote("shirt").extraPoints());
            assertEquals(approved.getApprovedAt(),shopRespond(d.getId(),"APPROVED","Retry").getApprovedAt());
            if(previous!=null) assertEquals("SUPERSEDED",deals.findById(previous).orElseThrow().getStatus());
            previous=d.getId();
        }
    }

    @Test void historicalOrderKeepsDealAfterReplacementEndOrPolicyEdit() {
        var first=shopRespond(propose(2).getId(),"APPROVED",null);
        Instant oldOrder=first.getApprovedAt().plusNanos(1);
        var second=shopRespond(propose(3).getId(),"APPROVED",null);
        assertEquals(0,service.rate(kol,"shirt","shop",oldOrder,base).totalRate().compareTo(BigDecimal.valueOf(12)));
        Instant newOrder=second.getApprovedAt().plusNanos(1);
        shopRespond(second.getId(),"ENDED",null);
        base.setCommissionRate(BigDecimal.valueOf(20));policies.saveAndFlush(base);
        assertEquals(0,service.rate(kol,"shirt","shop",newOrder,base).totalRate().compareTo(BigDecimal.valueOf(13)));
        assertEquals(0,service.quote("shirt").totalRate().compareTo(BigDecimal.valueOf(20)));
        assertEquals(0,service.rate(kol,"shirt","other-shop",oldOrder,base).totalRate().compareTo(BigDecimal.valueOf(20)));
        assertEquals(0,service.rate("another-kol","shirt","shop",oldOrder,base).totalRate().compareTo(BigDecimal.valueOf(20)));
    }

    @Test void approvalNeverAppliesRetroactivelyAndAmbiguousLegacyTimeGetsNoDeal() {
        Instant before=Instant.now().minusSeconds(1);
        shopRespond(propose(3).getId(),"APPROVED",null);
        assertEquals(0,service.rate(kol,"shirt","shop",before,base).extraPoints());
        assertEquals(0,service.rate(kol,"shirt","shop",null,base).extraPoints());
    }

    @Test void ownerApprovalAndKolCancellationAreAuthorized() {
        var pending=propose(1);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).requireStoreOwner("shop");
        assertThrows(ResponseStatusException.class,()->shopRespond(pending.getId(),"APPROVED",null));
        assertEquals("PENDING",deals.findById(pending.getId()).orElseThrow().getStatus());
        var other=profiles.saveAndFlush(CollaboratorProfile.builder().userId("other-user").approvalStatus(CollaboratorStatus.APPROVED).build());
        when(access.userId()).thenReturn(other.getUserId());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.cancel(pending.getId())).getStatusCode().value());
        when(access.userId()).thenReturn("kol-user");
        assertEquals("CANCELLED",service.cancel(pending.getId()).getStatus());
        assertEquals("CANCELLED",service.cancel(pending.getId()).getStatus());
    }

    @Test void selfPurchaseSuspendedKolChangedPolicyAndExcessiveTotalAreBlocked() {
        when(access.ownsStore("shop")).thenReturn(true);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->propose(1)).getStatusCode().value());
        when(access.ownsStore("shop")).thenReturn(false);
        var pending=propose(1);
        base.setCommissionRate(BigDecimal.valueOf(11));base=policies.saveAndFlush(base);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->shopRespond(pending.getId(),"APPROVED",null)).getStatusCode().value());
        service.cancel(pending.getId());
        base.setCommissionRate(BigDecimal.valueOf(99));base=policies.saveAndFlush(base);
        assertThrows(ResponseStatusException.class,()->propose(3));
        var profile=profiles.findById(kol).orElseThrow();profile.setApprovalStatus(CollaboratorStatus.SUSPENDED);profiles.saveAndFlush(profile);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.quote("shirt")).getStatusCode().value());
    }

    @Test void rejectedProposalDoesNotApplyAndConcurrentRequestsCannotCreateTwoPendingDeals() throws Exception {
        var rejected=shopRespond(propose(1).getId(),"REJECTED","Not suitable");
        assertEquals("REJECTED",rejected.getStatus()); assertEquals(0,service.quote("shirt").extraPoints());
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();try{propose(2);return true;}catch(ResponseStatusException ex){return false;}}));
            start.countDown();int successes=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);
        }
    }

    @Test void httpValidatesPointsAndSeparatesMineFromOwnedShop() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/referrals/commission-deals").with(jwt()).contentType("application/json").content("{\"productId\":\"shirt\",\"extraPoints\":4,\"message\":\"Promote the shirt\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/referrals/commission-deals/mine")).andExpect(status().isUnauthorized());
        propose(2);
        mvc.perform(get("/api/v1/referrals/commission-deals/mine").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].extraPoints").value(2));
        when(collaboration.stores(any(),eq(false))).thenReturn(List.of(Map.of("storeId","unrelated")));
        mvc.perform(get("/api/v1/referrals/commission-deals/shop").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    }

    @Test void postgresDefaultSeedCreatesTenOnceAndPreservesOperatorDecisions() {
        Assumptions.assumeTrue(System.getenv("SCANMS_TEST_POSTGRES_PREFIX")!=null);
        policies.deleteAll();defaultSeed.seed();defaultSeed.seed();
        assertEquals(1,policies.count());
        var seeded=policies.findAll().getFirst();assertEquals(0,seeded.getCommissionRate().compareTo(BigDecimal.TEN));
        seeded.setCommissionRate(BigDecimal.valueOf(15));seeded.setStatus(CommissionPolicyStatus.INACTIVE);policies.saveAndFlush(seeded);
        defaultSeed.seed();assertEquals(1,policies.count());
        assertEquals(CommissionPolicyStatus.INACTIVE,policies.findAll().getFirst().getStatus());
        assertEquals(0,policies.findAll().getFirst().getCommissionRate().compareTo(BigDecimal.valueOf(15)));
    }
}
