package com.scanms.product.service;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.dto.request.LiveBroadcastHeartbeat;
import com.scanms.product.entity.Livestream;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.impl.LiveBroadcastServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveBroadcastPolicyTest {
    private final LivestreamBroadcastPolicy policy = new LivestreamBroadcastPolicy();

    @Test
    void noInitialCameraClosesAfterGraceAndStaleHeartbeatCannotKeepLiveForever() {
        var now = Instant.now();
        var live = Livestream.builder().status(LivestreamStatus.LIVE).build();
        policy.start(live, now);
        assertFalse(policy.available(live, now));
        assertFalse(policy.closeIfDisconnected(live, now.plusSeconds(119)));
        assertTrue(policy.closeIfDisconnected(live, now.plusSeconds(120)));
        assertEquals(LivestreamStatus.ENDED, live.getStatus());
        assertEquals("HOST_DISCONNECTED", ((Map<?, ?>) live.getParticipantRefs().get("lifecycle")).get("reason"));

        live.setStatus(LivestreamStatus.LIVE);
        policy.save(live, Map.of("state", "LIVE", "lastHeartbeatAt", now.toString()));
        assertTrue(policy.available(live, now.plusSeconds(29)));
        assertFalse(policy.closeIfDisconnected(live, now.plusSeconds(30)));
        assertEquals("RECONNECTING", policy.state(live).get("state"));
        assertTrue(policy.closeIfDisconnected(live, now.plusSeconds(150)));
    }

    @Test
    void reconnectKeepsSameSessionAndOldDisconnectCannotOverwriteNewPublisher() {
        var lives = mock(LivestreamRepository.class);
        var live = Livestream.builder().livestreamId("live").status(LivestreamStatus.LIVE).productRefs(Map.of("pinnedProductId", "product")).build();
        when(lives.lockById("live")).thenReturn(Optional.of(live));
        var service = new LiveBroadcastServiceImpl(lives, policy, new LivestreamSchedulePolicy());
        service.heartbeat("live", new LiveBroadcastHeartbeat("old", 100, 1, "LIVE"), "kol");
        service.heartbeat("live", new LiveBroadcastHeartbeat("old", 100, 2, "RECONNECTING"), "kol");
        assertFalse(policy.available(live, Instant.now()));
        service.heartbeat("live", new LiveBroadcastHeartbeat("new", 200, 1, "LIVE"), "kol");
        service.heartbeat("live", new LiveBroadcastHeartbeat("old", 100, 999, "RECONNECTING"), "kol");
        assertTrue(policy.available(live, Instant.now()));
        assertEquals("new", policy.state(live).get("publisherId"));
        assertEquals("product", live.getProductRefs().get("pinnedProductId"));
        assertEquals(LivestreamStatus.LIVE, live.getStatus());
    }
}
