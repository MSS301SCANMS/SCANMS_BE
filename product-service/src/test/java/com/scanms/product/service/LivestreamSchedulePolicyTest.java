package com.scanms.product.service;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.entity.Livestream;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LivestreamSchedulePolicyTest {
    private final LivestreamSchedulePolicy policy = new LivestreamSchedulePolicy();
    private final Instant now = Instant.parse("2026-10-26T18:00:00Z");

    @Test
    void missedScheduleExpiresAtTheExactEndAndKeepsInviteHistory() {
        var live = Livestream.builder().status(LivestreamStatus.SCHEDULED)
                .participantRefs(Map.of("endsAt", "2026-10-27T01:00:00+07:00", "inviteStatus", "PENDING")).build();
        assertTrue(policy.closeIfElapsed(live, now));
        assertEquals(LivestreamStatus.CANCELLED, live.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 26, 18, 0), live.getEndedAt());
        assertEquals("PENDING", live.getParticipantRefs().get("inviteStatus"));
        assertEquals("SCHEDULE_EXPIRED", ((Map<?, ?>) live.getParticipantRefs().get("lifecycle")).get("reason"));
        assertFalse(policy.closeIfElapsed(live, now.plusSeconds(60)));
    }

    @Test
    void aRunningSessionEndsRatherThanBeingMarkedAsNeverBroadcast() {
        var live = Livestream.builder().status(LivestreamStatus.LIVE).startedAt(LocalDateTime.of(2026, 10, 26, 17, 0))
                .participantRefs(Map.of("endsAt", "2026-10-26T17:59:59")).build();
        assertTrue(policy.closeIfElapsed(live, now));
        assertEquals(LivestreamStatus.ENDED, live.getStatus());
        assertEquals("TIME_LIMIT_REACHED", ((Map<?, ?>) live.getParticipantRefs().get("lifecycle")).get("reason"));
    }

    @Test
    void crossingMidnightDoesNotEndAnOvernightSessionBeforeItsActualDeadline() {
        var live = Livestream.builder().status(LivestreamStatus.LIVE)
                .participantRefs(Map.of("endsAt", "2026-10-27T02:00:00+07:00")).build();
        assertFalse(policy.closeIfElapsed(live, now));
        assertEquals(LivestreamStatus.LIVE, live.getStatus());
    }

    @Test
    void missingOrMalformedDeadlinesDoNotInventAnEndTime() {
        for (var refs : java.util.List.of(Map.<String, Object>of(), Map.<String, Object>of("endsAt", "not-a-date"))) {
            var live = Livestream.builder().status(LivestreamStatus.SCHEDULED).participantRefs(refs).build();
            assertFalse(policy.closeIfElapsed(live, now));
            assertEquals(LivestreamStatus.SCHEDULED, live.getStatus());
        }
    }
}
