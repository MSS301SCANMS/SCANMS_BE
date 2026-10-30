package com.scanms.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scanms.live.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class LivestreamLifecycleScheduler {
    private final LivestreamLifecycleService lifecycle;

    @Scheduled(initialDelayString = "${scanms.live.lifecycle.initial-delay-ms:1000}",
            fixedDelayString = "${scanms.live.lifecycle.delay-ms:30000}")
    public void closeElapsedSessions() {
        int closed = lifecycle.closeElapsedSessions();
        if (closed > 0) log.info("Automatically closed {} elapsed livestream sessions", closed);
    }
}
