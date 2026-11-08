package com.scanms.product.service;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.entity.Livestream;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
        return live.getStatus() == LivestreamStatus.LIVE
                && plannedEnd(live).map(end -> !end.isAfter(Instant.now())).orElse(false);
    }

    public boolean closeIfElapsed(Livestream live, Instant now) {
        if (live.getStatus() != LivestreamStatus.LIVE) return false;
        var deadline = plannedEnd(live);
        if (deadline.isEmpty() || deadline.get().isAfter(now)) return false;

        live.setStatus(LivestreamStatus.ENDED);
        live.setEndedAt(LocalDateTime.ofInstant(deadline.get(), ZoneOffset.UTC));
        var refs = new LinkedHashMap<String, Object>(live.getParticipantRefs() == null ? Map.of() : live.getParticipantRefs());
        refs.put("lifecycle", Map.of(
                "reason", "TIME_LIMIT_REACHED",
                "closedBy", "SYSTEM",
                "closedAt", now.toString()));
        live.setParticipantRefs(refs);
        return true;
    }
}
