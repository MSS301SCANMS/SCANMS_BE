package com.scanms.product.service.impl;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.LivestreamReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LivestreamReactionServiceImpl implements LivestreamReactionService {
    private final LivestreamRepository repository;

    @Override
    @Transactional
    public long updateCount(String id, long count, Jwt actor) {
        if (actor == null || actor.getSubject() == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        if (count < 0 || count > 9007199254740991L) throw new AppException(ErrorCode.INVALID_REQUEST);
        var live = repository.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!Set.of(LivestreamStatus.LIVE, LivestreamStatus.ENDED).contains(live.getStatus()))
            throw new AppException(ErrorCode.CONFLICT, "Only started sessions accept reaction statistics");
        var stats = new LinkedHashMap<String, Object>(live.getStatistics() == null ? Map.of() : live.getStatistics());
        long previous = stats.get("likeCount") instanceof Number value ? value.longValue() : 0;
        long total = Math.max(previous, count);
        stats.put("likeCount", total);
        live.setStatistics(stats);
        return total;
    }
}
