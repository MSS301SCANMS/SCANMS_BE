package com.scanms.product.service;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.entity.Livestream;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class LivestreamSchedulePolicy {
    public Optional<Instant> plannedEnd(Livestream live) {
        Object value = live.getParticipantRefs() == null ? null : live.getParticipantRefs().get("endsAt");
        if (value == null) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(value.toString()).toInstant());
        } catch (DateTimeParseException ignored) {
            try {
                // The existing LocalDateTime API contract stores UTC without a suffix.
                return Optional.of(LocalDateTime.parse(value.toString()).toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException invalid) {
                return Optional.empty();
            }
        }
    }

    public boolean hasElapsed(Livestream live) {
        return plannedEnd(live).map(end -> !end.isAfter(Instant.now())).orElse(false);
    }

    public boolean closeIfElapsed(Livestream live, Instant now) {
        if (!Set.of(LivestreamStatus.SCHEDULED, LivestreamStatus.LIVE).contains(live.getStatus())) return false;
        var deadline = plannedEnd(live);
        if (deadline.isEmpty() || deadline.get().isAfter(now)) return false;

        boolean broadcast = live.getStatus() == LivestreamStatus.LIVE || live.getStartedAt() != null;
        live.setStatus(broadcast ? LivestreamStatus.ENDED : LivestreamStatus.CANCELLED);
        live.setEndedAt(LocalDateTime.ofInstant(deadline.get(), ZoneOffset.UTC));
        var refs = new LinkedHashMap<String, Object>(live.getParticipantRefs() == null ? Map.of() : live.getParticipantRefs());
        refs.put("lifecycle", Map.of(
                "reason", broadcast ? "TIME_LIMIT_REACHED" : "SCHEDULE_EXPIRED",
                "closedBy", "SYSTEM",
                "closedAt", now.toString()));
        live.setParticipantRefs(refs);
        return true;
    }
}
