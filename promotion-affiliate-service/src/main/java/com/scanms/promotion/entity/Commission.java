package com.scanms.promotion.entity;

import com.scanms.promotion.constant.CommissionStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "commissions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Commission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String commissionId;

    private String orderItemId;

    private String collaboratorId;

    private String referralLinkId;

    private String commissionPolicyId;

    @Column(name = "basis_amount_vnd")
    private Long basisAmountVnd;

    @Column(name = "rate_snapshot", precision = 10, scale = 6)
    private BigDecimal rateSnapshot;

    @Column(name = "commission_amount_vnd")
    private Long commissionAmountVnd;

    @Enumerated(EnumType.STRING)
    private CommissionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> adjustmentHistory;

    private String transferReference;

    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
