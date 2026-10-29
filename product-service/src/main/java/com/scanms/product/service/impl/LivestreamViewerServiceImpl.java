package com.scanms.product.service.impl;

import com.scanms.product.service.LivestreamViewerService;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class LivestreamViewerServiceImpl implements LivestreamViewerService {
    private final LivestreamRepository repository;
    private final com.scanms.product.service.LivestreamSchedulePolicy schedule;

    @Transactional
    public void record(String id, String visitId, Jwt viewer) {
        if (viewer == null || viewer.getSubject() == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        var live = repository.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (live.getStatus() != LivestreamStatus.LIVE || schedule.hasElapsed(live))
            throw new AppException(ErrorCode.CONFLICT, "Only live sessions accept views");
        var stats = new LinkedHashMap<String, Object>(live.getStatistics() == null ? Map.of() : live.getStatistics());
        var visits = values(stats.get("visits"));
        var users = values(stats.get("viewers"));
        String visit = hash(visitId), user = hash(viewer.getIssuer() + "|" + viewer.getSubject());
        if (!visits.add(visit)) return;
        if (visits.size() > 100000) throw new AppException(ErrorCode.CONFLICT, "Livestream view history limit reached");
        live.setViewerCount(Math.addExact(live.getViewerCount() == null ? 0 : live.getViewerCount(), 1));
        if (users.add(user))
            live.setUniqueViewerCount(Math.addExact(live.getUniqueViewerCount() == null ? 0 : live.getUniqueViewerCount(), 1));
        stats.put("visits", new ArrayList<>(visits));
        stats.put("viewers", new ArrayList<>(users));
        live.setStatistics(stats);
    }

    private Set<String> values(Object raw) {
        var values = new LinkedHashSet<String>();
        if (raw instanceof Collection<?> rows) rows.forEach(row -> values.add(row.toString()));
        return values;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
