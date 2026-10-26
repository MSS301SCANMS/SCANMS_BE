package com.scanms.product.service.impl;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.LivestreamLifecycleService;
import com.scanms.product.service.LivestreamSchedulePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LivestreamLifecycleServiceImpl implements LivestreamLifecycleService {
    private final LivestreamRepository repository;
    private final LivestreamSchedulePolicy schedule;

    @Override
    @Transactional
    public int closeElapsedSessions() {
        var ids = repository.findIdsByStatusIn(List.of(LivestreamStatus.SCHEDULED, LivestreamStatus.LIVE));
        int count = 0;
        for (String id : ids) {
            var live = repository.lockById(id);
            if (live.isPresent() && schedule.closeIfElapsed(live.get(), Instant.now())) count++;
        }
        return count;
    }
}
