package com.scanms.payment.entity;

import com.scanms.payment.constant.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "wallet_transactions", uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_transaction_idempotency", columnNames = "idempotency_key"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String transactionId;
    @Column(nullable = false)
    private String walletId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private WalletTransactionType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private WalletTransactionDirection direction;
    @Column(nullable = false)
    private Long amountVnd;
    @Column(nullable = false)
    private Long balanceBeforeVnd;
    @Column(nullable = false)
    private Long balanceAfterVnd;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private WalletTransactionStatus status;
    private String referenceType;
    private String referenceId;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    private String description;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    private Instant completedAt;
}
