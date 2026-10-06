package com.scanms.promotion.entity;

import com.scanms.promotion.constant.CollaboratorStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "collaborator_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CollaboratorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String collaboratorId;

    private String userId;

    @Enumerated(EnumType.STRING)
    private CollaboratorStatus approvalStatus;

    private LocalDateTime joinedAt;

    private String policyVersion;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
