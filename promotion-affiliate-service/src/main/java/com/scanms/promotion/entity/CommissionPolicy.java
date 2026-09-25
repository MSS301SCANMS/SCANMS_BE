package com.scanms.promotion.entity;

import com.scanms.promotion.constant.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "commission_policies")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionPolicy {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String policyId;
    @Column(nullable = false)
    private String policyName;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private CommissionPolicyScope scopeType;
    private String storeId;
    private String productId;
    private String livestreamId;
    private String collaboratorId;
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal commissionRate;
    @Column(nullable = false)
    private Integer returnWindowDays;
    @Column(nullable = false)
    private Instant validFrom;
    private Instant validTo;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private CommissionPolicyStatus status;
    @Column(nullable = false)
    private Integer priority;
    @Version
    private Long version;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

