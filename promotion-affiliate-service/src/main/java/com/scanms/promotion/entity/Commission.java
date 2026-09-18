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
    private UUID commissionId;

    private UUID orderItemId;

    private UUID collaboratorId;

    private UUID referralLinkId;

    private Long basisVnd;

    private BigDecimal rate;

    private Long amountVnd;

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
