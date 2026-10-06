package com.scanms.payment.entity;

import com.scanms.payment.constant.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;

@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_owner_currency", columnNames = {"owner_type", "owner_ref_id", "currency"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Wallet {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String walletId;
    @Enumerated(EnumType.STRING) @Column(name = "owner_type", nullable = false)
    private WalletOwnerType ownerType;
    @Column(name = "owner_ref_id", nullable = false)
    private String ownerRefId;
    @Column(nullable = false)
    private String currency;
    @Column(nullable = false)
    private Long availableBalanceVnd;
    @Column(nullable = false)
    private Long heldBalanceVnd;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private WalletStatus status;
    @Version
    private Long version;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

