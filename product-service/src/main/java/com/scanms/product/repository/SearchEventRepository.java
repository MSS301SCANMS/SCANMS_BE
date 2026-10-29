package com.scanms.product.repository;

import com.scanms.product.entity.SearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SearchEventRepository extends JpaRepository<SearchEvent, String> {
    Optional<SearchEvent> findByActorHashAndRequestId(String actorHash, String requestId);

    List<SearchEvent> findTop100ByActorHashOrderByCreatedAtDesc(String actorHash);

    long deleteByActorHash(String actorHash);

    long deleteByCreatedAtBefore(Instant cutoff);
}
