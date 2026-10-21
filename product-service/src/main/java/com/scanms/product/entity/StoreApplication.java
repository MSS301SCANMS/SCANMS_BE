package com.scanms.product.entity;

import com.scanms.product.constant.StoreApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;
import java.util.Map;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "store_applications")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StoreApplication {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String applicationId;
    @Column(nullable = false)
    private String applicantUserId;
    @Column(nullable = false)
    private String proposedStoreName;
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> onboardingData;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private StoreApplicationStatus status;
    private String reviewedByUserId;
    private String reviewNote;
    private Instant submittedAt;
    private Instant reviewedAt;
    @Version
    private Long version;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

