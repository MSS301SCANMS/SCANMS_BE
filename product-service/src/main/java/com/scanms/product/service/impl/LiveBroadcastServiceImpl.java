package com.scanms.product.service.impl;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.dto.request.LiveBroadcastHeartbeat;
import com.scanms.product.exception.*;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class LiveBroadcastServiceImpl implements LiveBroadcastService {
    private final LivestreamRepository lives;
    private final LivestreamBroadcastPolicy broadcast;
    private final LivestreamSchedulePolicy schedule;

    @Override
    public Map<String, Object> heartbeat(String id, LiveBroadcastHeartbeat input, String subject) {
        var live = lives.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        var now = Instant.now();
        schedule.closeIfElapsed(live, now);
        broadcast.closeIfDisconnected(live, now);
        if (live.getStatus() != LivestreamStatus.LIVE)
            throw new AppException(ErrorCode.CONFLICT, "Phiên livestream đã kết thúc hoặc chưa bắt đầu.");
        var state = broadcast.state(live);
        long epoch = ((Number) state.getOrDefault("epoch", 0L)).longValue();
        long sequence = ((Number) state.getOrDefault("sequence", 0L)).longValue();
        if (input.epoch() < epoch || input.epoch() == epoch && input.sequence() <= sequence) return state;
        if (input.epoch() == epoch && !input.publisherId().equals(state.get("publisherId")))
            throw new AppException(ErrorCode.CONFLICT, "Publisher generation mismatch");
        if (input.epoch() > epoch && broadcast.available(live, now) && !subject.equals(state.get("publisherSubject")))
            throw new AppException(ErrorCode.CONFLICT, "Phiên đang có người phát sóng.");
        if (input.epoch() > epoch && !"LIVE".equals(input.state())) return state;
        state.put("publisherId", input.publisherId());
        state.put("publisherSubject", subject);
        state.put("epoch", input.epoch());
        state.put("sequence", input.sequence());
        state.put("state", input.state());
        if ("LIVE".equals(input.state())) {
            state.put("lastHeartbeatAt", now.toString());
            state.remove("reconnectUntil");
            state.remove("disconnectedAt");
        } else {
            state.putIfAbsent("disconnectedAt", now.toString());
            state.putIfAbsent("reconnectUntil", now.plusSeconds(broadcast.graceSeconds()).toString());
        }
        broadcast.save(live, state);
        return state;
    }
}
