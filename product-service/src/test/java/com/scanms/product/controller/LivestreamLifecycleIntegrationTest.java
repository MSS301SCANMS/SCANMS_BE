package com.scanms.product.controller;

import com.scanms.product.constant.*;
import com.scanms.product.dto.request.CreateLiveOfferRequest;
import com.scanms.product.dto.request.InventoryReservationInput;
import com.scanms.product.dto.request.InventoryReservationLine;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:live-lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.catalog.seed-enabled=false", "scanms.catalog.cache-enabled=false", "scanms.live.lifecycle.enabled=false", "logging.level.root=WARN"})
class LivestreamLifecycleIntegrationTest {
    @Autowired
    LivestreamRepository lives;
    @Autowired
    LivestreamService service;
    @Autowired
    LivestreamLifecycleService lifecycle;
    @Autowired
    LiveCommerceService commerce;
    @Autowired
    InventoryCheckoutController inventory;
    @Autowired
    StoreRepository stores;
    @Autowired
    ProductRepository products;
    @Autowired
    ProductVariantRepository variants;
    @MockitoBean
    ProductAccess access;
    @MockitoBean
    JwtDecoder decoder;
    String storeId, productId, variantId;

    @BeforeEach
    void setup() {
        lives.deleteAll();
        variants.deleteAll();
        products.deleteAll();
        stores.deleteAll();
        var store = stores.saveAndFlush(Store.builder().ownerUserId("owner").name("Store").approvalStatus(StoreStatus.APPROVED).build());
        storeId = store.getStoreId();
        var product = products.saveAndFlush(Product.builder().storeId(storeId).name("Product").priceVnd(1000L).status(ProductStatus.ACTIVE).build());
        productId = product.getProductId();
        variantId = variants.saveAndFlush(ProductVariant.builder().productId(productId).sku("LIVE-SKU").priceVnd(1000L).stockQuantity(10).status(ProductVariantStatus.ACTIVE).build()).getVariantId();
        when(access.requireApprovedStore(anyString())).thenReturn(store);
    }

    Livestream live(String title, LivestreamStatus status, Instant deadline) {
        return lives.saveAndFlush(Livestream.builder().title(title).storeId(storeId).status(status)
                .scheduledAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
                .productRefs(Map.of("productIds", List.of(productId)))
                .participantRefs(Map.of("endsAt", deadline.toString(), "inviteStatus", "PENDING")).build());
    }

    @Test
    void sweepClosesOverdueBroadcastsAndKeepsUnstartedAppointments() {
        var missed = live("Missed", LivestreamStatus.SCHEDULED, Instant.now().minusSeconds(60));
        var running = live("Running", LivestreamStatus.LIVE, Instant.now().minusSeconds(60));
        var future = live("Future", LivestreamStatus.SCHEDULED, Instant.now().plusSeconds(3600));
        assertEquals(1, lifecycle.closeElapsedSessions());
        assertEquals(LivestreamStatus.SCHEDULED, lives.findById(missed.getLivestreamId()).orElseThrow().getStatus());
        assertEquals(LivestreamStatus.ENDED, lives.findById(running.getLivestreamId()).orElseThrow().getStatus());
        assertEquals(LivestreamStatus.SCHEDULED, lives.findById(future.getLivestreamId()).orElseThrow().getStatus());
        assertEquals(0, lifecycle.closeElapsedSessions());
    }

    @Test
    void aReadRepairsStaleStatusWithoutWaitingForTheScheduler() {
        var missed = live("Read repair", LivestreamStatus.SCHEDULED, Instant.now().minusSeconds(30));
        assertEquals(LivestreamStatus.SCHEDULED, service.getById(missed.getLivestreamId()).status());
        assertEquals(LivestreamStatus.SCHEDULED, lives.findById(missed.getLivestreamId()).orElseThrow().getStatus());
        var overdue = live("List repair", LivestreamStatus.LIVE, Instant.now().minusSeconds(30));
        assertTrue(service.findAll().stream().anyMatch(row -> row.livestreamId().equals(overdue.getLivestreamId()) && row.status() == LivestreamStatus.ENDED));
    }

    @Test
    void expiredBroadcastsCannotReceiveNewOffersOrInventoryReservations() {
        var overdue = live("Overdue LIVE", LivestreamStatus.LIVE, Instant.now().minusSeconds(10));
        assertThrows(com.scanms.product.exception.AppException.class, () -> commerce.createOffer(overdue.getLivestreamId(), new CreateLiveOfferRequest(productId, variantId, 900L, 1L, null, null)));
        var token = Jwt.withTokenValue("internal").header("alg", "RS256").subject("order-service").claim("realm_access", Map.of("roles", List.of("ORDER_INTERNAL"))).build();
        var request = new InventoryReservationInput(List.of(new InventoryReservationLine(productId, variantId, 1, overdue.getLivestreamId())));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> inventory.quote(request, token));
        assertEquals(10, variants.findById(variantId).orElseThrow().getStockQuantity());
    }

    @Test
    void startingEarlyOrAfterTheWholeAppointmentPreservesItsDuration() {
        for (int hours : List.of(2, -3)) {
            var plannedStart = LocalDateTime.now(ZoneOffset.UTC).plusHours(hours).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var plannedEnd = plannedStart.plusMinutes(45).toInstant(ZoneOffset.UTC);
            var pending = live("Flexible start", LivestreamStatus.SCHEDULED, plannedEnd);
            pending.setScheduledAt(plannedStart);
            lives.saveAndFlush(pending);
            var before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
            var started = service.transition(pending.getLivestreamId(), LivestreamStatus.LIVE);
            assertEquals(LivestreamStatus.LIVE, started.status());
            var stored = lives.findById(pending.getLivestreamId()).orElseThrow();
            assertFalse(stored.getStartedAt().isBefore(before));
            assertFalse(stored.getStartedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
            var effectiveEnd = Instant.parse(stored.getParticipantRefs().get("endsAt").toString());
            assertEquals(45, Duration.between(stored.getStartedAt().toInstant(ZoneOffset.UTC), effectiveEnd).toMinutes());
            assertEquals(plannedEnd, Instant.parse(stored.getParticipantRefs().get("scheduledEndsAt").toString()));
            assertEquals(plannedStart.truncatedTo(java.time.temporal.ChronoUnit.MICROS), stored.getScheduledAt());
            assertThrows(com.scanms.product.exception.AppException.class, () -> service.transition(pending.getLivestreamId(), LivestreamStatus.LIVE));
            assertEquals(effectiveEnd, Instant.parse(lives.findById(pending.getLivestreamId()).orElseThrow().getParticipantRefs().get("endsAt").toString()));
        }
    }

    @Test
    void closedSessionsRemainClosedAndShopOwnershipIsStillRequired() {
        for (var status : List.of(LivestreamStatus.ENDED, LivestreamStatus.CANCELLED)) {
            var closed = live("Closed", status, Instant.now().minusSeconds(60));
            assertThrows(com.scanms.product.exception.AppException.class, () -> service.transition(closed.getLivestreamId(), LivestreamStatus.LIVE));
            assertEquals(status, lives.findById(closed.getLivestreamId()).orElseThrow().getStatus());
        }
        var pending = live("Not your store", LivestreamStatus.SCHEDULED, Instant.now().plusSeconds(3600));
        org.mockito.Mockito.doThrow(new com.scanms.product.exception.AppException(com.scanms.product.exception.ErrorCode.FORBIDDEN))
                .when(access).requireApprovedStore(storeId);
        assertThrows(com.scanms.product.exception.AppException.class, () -> service.transition(pending.getLivestreamId(), LivestreamStatus.LIVE));
        assertEquals(LivestreamStatus.SCHEDULED, lives.findById(pending.getLivestreamId()).orElseThrow().getStatus());
    }
}
