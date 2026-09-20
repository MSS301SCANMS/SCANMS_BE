package com.scanms.catalog.entity;

import com.scanms.catalog.constant.LivestreamStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "livestreams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Livestream {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID livestreamId;

    private UUID storeId;

    private String title;

    @Enumerated(EnumType.STRING)
    private LivestreamStatus status;

    private String providerSessionId;

    private String playbackRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> productRefs;

    private LocalDateTime scheduledAt;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
