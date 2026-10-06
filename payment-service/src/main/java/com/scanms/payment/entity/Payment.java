package com.scanms.payment.entity;

import com.scanms.payment.constant.PaymentStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payment_key", columnNames = "idempotencyKey"),
    @UniqueConstraint(name = "uk_payment_provider_order", columnNames = "providerOrderCode"),
    @UniqueConstraint(name = "uk_payment_active_order", columnNames = "activeOrderRef")})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String paymentId;

    private String orderId;
    private String activeOrderRef;
    private String payerId;
    private String walletId;
    private String purpose;
    private Long providerOrderCode;
    private String checkoutUrl;
    @Column(columnDefinition = "text") private String qrCode;
    private String bin;
    private String accountNumber;
    private String accountName;
    private String description;
    private LocalDateTime expiresAt;
    @Version private Long version;

    private String providerCode;

    private String transactionId;

    private Long amountVnd;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String idempotencyKey;

    private LocalDateTime verifiedAt;

    private String failureReason;
    private String orderSyncStatus;
    private String resolutionReference;
    @Column(length=500) private String resolutionReason;
    private String resolvedBy;
    private Long receivedAmountVnd;
    private Long resolutionAmountVnd;
    private Long surplusRefundedAmountVnd;
    private String surplusReference;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
