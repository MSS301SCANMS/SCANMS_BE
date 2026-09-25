package com.scanms.payment.entity;

import com.scanms.payment.constant.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fee_configs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FeeConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String feeConfigId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private FeeType feeType;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private FeeCalculationType calculationType;
    private BigDecimal ratePercent;
    private Long fixedAmountVnd;
    private Long minFeeVnd;
    private Long maxFeeVnd;
    private Instant validFrom;
    private Instant validTo;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private FeeConfigStatus status;
    @Version
    private Long version;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

