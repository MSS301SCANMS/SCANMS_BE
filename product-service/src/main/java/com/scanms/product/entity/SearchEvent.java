package com.scanms.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "search_events", uniqueConstraints = @UniqueConstraint(name = "uk_search_actor_request", columnNames = {"actorHash", "requestId"}),
        indexes = {@Index(name = "ix_search_actor_time", columnList = "actorHash,createdAt"), @Index(name = "ix_search_created", columnList = "createdAt")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String eventId;
    @Column(nullable = false, length = 64)
    private String actorHash;
    @Column(nullable = false, length = 100)
    private String requestId;
    @Column(nullable = false, length = 200)
    private String keyword;
    private int resultCount;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> filters;
    @Column(nullable = false)
    private Instant createdAt;
}
