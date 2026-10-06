package com.scanms.promotion.entity;

import com.scanms.promotion.constant.VoucherRedemptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "voucher_redemptions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VoucherRedemption {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String redemptionId;
    @Column(nullable = false)
    private String voucherId;
    @Column(nullable = false)
    private String userId;
    @Column(nullable = false)
    private String orderId;
    @Column(nullable = false)
    private Long discountAmountVnd;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private VoucherRedemptionStatus status;
    @CreationTimestamp @Column(updatable = false)
    private Instant redeemedAt;
    private Instant cancelledAt;
}

