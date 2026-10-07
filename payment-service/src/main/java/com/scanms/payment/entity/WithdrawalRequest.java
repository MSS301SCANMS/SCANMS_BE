package com.scanms.payment.entity;

import com.scanms.payment.constant.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "withdrawal_requests", uniqueConstraints = @UniqueConstraint(name = "uk_withdrawal_key", columnNames = "idempotencyKey"))
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
    @Column(length=500) private String failureReason;
    private String idempotencyKey;
    private Long feeVnd;
    private Long netAmountVnd;
    @Column(columnDefinition="text") private String feeSnapshot;
    @Column(columnDefinition="text") private String destinationSnapshot;
    private Instant scheduledFor;
    @Version private Long version;
    @CreationTimestamp @Column(updatable = false)
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant processedAt;
    private Instant completedAt;
}

