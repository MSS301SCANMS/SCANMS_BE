package com.scanms.product.entity;

import com.scanms.product.constant.LivestreamStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
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
    private String livestreamId;

    private String storeId;

    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    private LivestreamStatus status;

    private String providerSessionId;

    private String playbackRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> productRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> participantRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> collaboratorRefs;

    private Long viewerCount;

    private Long uniqueViewerCount;

    private Long peakConcurrentViewerCount;

    private LocalDateTime scheduledAt;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
