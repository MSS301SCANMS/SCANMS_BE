package com.scanms.promotion.controller;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.dto.request.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:web-samples;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class SampleRequestWebIntegrationTest {
    @Autowired SampleRequestService service;
    @Autowired SampleRequestRepository samples;
    @Autowired CollaboratorProfileRepository profiles;
    @Autowired PartnershipInvitationRepository invitations;
    @Autowired ProductCommissionDealRepository deals;
    @MockitoBean VoucherAccess access;
    @MockitoBean ReferralProductAccess catalog;
    @MockitoBean JwtDecoder decoder;
    String kol;

    @BeforeEach void setup() {
        samples.deleteAll(); invitations.deleteAll(); deals.deleteAll(); profiles.deleteAll();
        kol = profiles.saveAndFlush(CollaboratorProfile.builder().userId("kol").approvalStatus(CollaboratorStatus.APPROVED).build()).getCollaboratorId();
        var invitation = new PartnershipInvitation();
        invitation.setStoreId("shop"); invitation.setCollaboratorId(kol); invitation.setStatus("ACCEPTED"); invitation.setCreatedAt(LocalDateTime.now());
        invitations.saveAndFlush(invitation);
        when(access.userId()).thenReturn("kol");
        when(access.ownedStoreIds()).thenReturn(Set.of("shop"));
        doReturn(Map.of("id", "product", "title", "Sample Shirt", "storeId", "shop", "priceVnd", 100000L)).when(catalog).product("product");
    }
    SampleCreateRequest request(String id) { return new SampleCreateRequest(id, "product", "Please send a review sample", "KOL", "0901234567", "100 Test Street, Ward, City"); }
    SampleDecisionRequest decision(SampleRequest row, String note) { return new SampleDecisionRequest(row.getVersion(), note, null, null); }

    @Test void requestRetryDeduplicatesAndOnlyParticipantsSeeAddress() {
        String id = UUID.randomUUID().toString();
        var row = service.create(request(id));
        assertEquals(id, service.create(request(id)).getId());
        assertEquals(1, samples.count());
        assertThrows(ResponseStatusException.class, () -> service.create(request(UUID.randomUUID().toString())));
        when(access.userId()).thenReturn("outsider");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(access).requireStoreOwner("shop");
        assertThrows(ResponseStatusException.class, () -> service.get(id));
        assertThrows(ResponseStatusException.class, () -> service.decide(id, "approve", decision(row, "intrusion")));
    }

    @Test void approvalIsIndependentOfCommissionAndDeliveryFollowsOwnerThenKol() {
        var row = service.create(request(UUID.randomUUID().toString()));
        assertThrows(ResponseStatusException.class, () -> service.decide(row.getId(), "approve", decision(row, "self")));
        when(access.userId()).thenReturn("shop-owner");
        var approved = service.decide(row.getId(), "approve", decision(row, "Approved sample only"));
        assertEquals("APPROVED", approved.getStatus()); assertEquals(0, deals.count());
        assertThrows(ResponseStatusException.class, () -> service.decide(row.getId(), "approve", decision(row, "Replay")));
        var shipped = service.decide(row.getId(), "ship", new SampleDecisionRequest(approved.getVersion(), "Sent", "Carrier", "TRACK-123"));
        assertEquals("SHIPPED", shipped.getStatus());
        assertThrows(ResponseStatusException.class, () -> service.decide(row.getId(), "receive", decision(shipped, "self")));
        when(access.userId()).thenReturn("kol");
        var received = service.decide(row.getId(), "receive", decision(shipped, "Received"));
        assertEquals("RECEIVED", received.getStatus()); assertNotNull(received.getReceivedAt());
    }

    @Test void rejectedRequestKeepsResponseAndAllowsANewRequest() {
        var row = service.create(request(UUID.randomUUID().toString()));
        when(access.userId()).thenReturn("shop-owner");
        assertThrows(ResponseStatusException.class, () -> service.decide(row.getId(), "reject", decision(row, "")));
        var rejected = service.decide(row.getId(), "reject", decision(row, "No sample budget"));
        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("No sample budget", rejected.getResponse());
        when(access.userId()).thenReturn("kol");
        assertEquals("PENDING", service.create(request(UUID.randomUUID().toString())).getStatus());
    }
}
