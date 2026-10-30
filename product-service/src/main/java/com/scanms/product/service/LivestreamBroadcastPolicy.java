package com.scanms.product.service;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.entity.Livestream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LivestreamBroadcastPolicy {
    @Value("${scanms.live.reconnect-grace-seconds:120}")
    private long graceSeconds = 120;
    @Value("${scanms.live.heartbeat-timeout-seconds:30}")
    private long heartbeatSeconds = 30;

    public Map<String, Object> state(Livestream live) {
        var value = new LinkedHashMap<String, Object>();
        if (live.getParticipantRefs() != null && live.getParticipantRefs().get("broadcast") instanceof Map<?, ?> data)
            data.forEach((key, item) -> value.put(key.toString(), item));
        return value;
    }

    public void save(Livestream live, Map<String, Object> state) {
        var refs = new LinkedHashMap<>(live.getParticipantRefs() == null ? Map.of() : live.getParticipantRefs());
        refs.put("broadcast", state);
        live.setParticipantRefs(refs);
    }

    public void start(Livestream live, Instant now) {
        save(live, Map.of("state", "CONNECTING", "reconnectUntil", now.plusSeconds(graceSeconds).toString()));
    }

    public boolean available(Livestream live, Instant now) {
        var state = state(live);
        var lastSeen = instant(state.get("lastHeartbeatAt"));
        return live.getStatus() == LivestreamStatus.LIVE && "LIVE".equals(state.get("state"))
                && lastSeen != null && lastSeen.plusSeconds(heartbeatSeconds).isAfter(now);
    }

    public boolean closeIfDisconnected(Livestream live, Instant now) {
        if (live.getStatus() != LivestreamStatus.LIVE) return false;
        var state = state(live);
        if (state.isEmpty()) return false; // Existing sessions without relay metadata still close by their schedule.
        if ("LIVE".equals(state.get("state")) && !available(live, now)) {
            var heartbeat = instant(state.get("lastHeartbeatAt"));
            var disconnected = heartbeat == null ? now : heartbeat.plusSeconds(heartbeatSeconds);
            state.put("state", "RECONNECTING");
            state.put("disconnectedAt", disconnected.toString());
            state.put("reconnectUntil", disconnected.plusSeconds(graceSeconds).toString());
            save(live, state);
        }
        var deadline = instant(state.get("reconnectUntil"));
        if (!"LIVE".equals(state.get("state")) && deadline != null && !deadline.isAfter(now)) {
            state.put("state", "ENDED");
            save(live, state);
            live.setStatus(LivestreamStatus.ENDED);
            live.setEndedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            var refs = new LinkedHashMap<>(live.getParticipantRefs());
            refs.put("lifecycle", Map.of("reason", "HOST_DISCONNECTED", "closedBy", "SYSTEM", "closedAt", now.toString()));
            live.setParticipantRefs(refs);
            return true;
        }
        return false;
    }

    public long graceSeconds() {
        return graceSeconds;
    }

    private Instant instant(Object value) {
        try {
            return value == null ? null : Instant.parse(value.toString());
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
