package com.scanms.order.entity;

import com.scanms.order.constant.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "discount_allocations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DiscountAllocation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String allocationId;
    @Column(nullable = false)
    private String orderItemId;
    private String voucherId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DiscountSourceType sourceType;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DiscountFundingType fundedBy;
    @Column(nullable = false)
    private Long allocatedAmountVnd;
    @Column(columnDefinition = "text")
    private String ruleSnapshotJson;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
}

