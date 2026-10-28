package com.scanms.product.service.impl;

import com.scanms.product.dto.request.SearchEventRequest;
import com.scanms.product.dto.response.SearchEventResponse;
import com.scanms.product.entity.SearchEvent;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.SearchEventMapper;
import com.scanms.product.repository.SearchEventRepository;
import com.scanms.product.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {
    private final SearchEventRepository repository;
    private final SearchEventMapper mapper;
    private final PlatformTransactionManager transactions;
    @Value("${scanms.search.retention-days:30}")
    private int retentionDays = 30;
    private static final Set<String> FILTERS = Set.of("category", "categoryGroup", "store", "sort", "min", "max", "inStock", "discount");

    @Override
    public SearchEventResponse record(SearchEventRequest input, Jwt jwt) {
        String actor = actor(jwt, input.sessionId());
        String keyword = input.keyword().strip().replaceAll("\\s+", " ");
        if (keyword.isEmpty()) throw new AppException(ErrorCode.INVALID_REQUEST);
        var previous = repository.findByActorHashAndRequestId(actor, input.requestId());
        if (previous.isPresent()) return mapper.toResponse(previous.get());
        var filters = new LinkedHashMap<String, String>();
        if (input.filters() != null) input.filters().forEach((key, value) -> {
            if (FILTERS.contains(key) && value != null) filters.put(key, value);
        });
        try {
            return new TransactionTemplate(transactions).execute(tx -> mapper.toResponse(repository.saveAndFlush(SearchEvent.builder()
                    .actorHash(actor).requestId(input.requestId()).keyword(keyword).resultCount(input.resultCount())
                    .filters(filters).createdAt(Instant.now()).build())));
        } catch (DataIntegrityViolationException ex) {
            return repository.findByActorHashAndRequestId(actor, input.requestId()).map(mapper::toResponse).orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchEventResponse> recent(Jwt jwt) {
        var seen = new HashSet<String>();
        return repository.findTop100ByActorHashOrderByCreatedAtDesc(actor(jwt, null)).stream()
                .filter(event -> seen.add(event.getKeyword().toLowerCase(Locale.ROOT))).limit(20).map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void clear(Jwt jwt) {
        repository.deleteByActorHash(actor(jwt, null));
    }

    @Scheduled(initialDelayString = "${scanms.search.cleanup-initial-delay-ms:60000}", fixedDelayString = "${scanms.search.cleanup-delay-ms:86400000}")
    @Transactional
    public void cleanup() {
        repository.deleteByCreatedAtBefore(Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS));
    }

    private String actor(Jwt jwt, String session) {
        if (jwt != null && (jwt.getSubject() == null || jwt.getSubject().isBlank()))
            throw new AppException(ErrorCode.UNAUTHORIZED);
        if (jwt == null && (session == null || !session.matches("[A-Za-z0-9_-]{16,100}")))
            throw new AppException(ErrorCode.UNAUTHORIZED);
        String identity = jwt == null ? "GUEST:" + session : "USER:" + jwt.getSubject();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
