package com.scanms.payment.entity;

import com.scanms.payment.constant.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "withdrawal_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WithdrawalRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String withdrawalId;
    @Column(nullable = false)
    private String walletId;
    @Column(nullable = false)
    private String bankAccountId;
    @Column(nullable = false)
    private Long amountVnd;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private WithdrawalStatus status;
    private String providerReference;
    private String failureReason;
    @CreationTimestamp @Column(updatable = false)
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant processedAt;
    private Instant completedAt;
}

