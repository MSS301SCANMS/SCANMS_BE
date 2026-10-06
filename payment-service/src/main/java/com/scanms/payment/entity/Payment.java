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
@Table(name = "payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String paymentId;

    private String orderId;

    private String providerCode;

    private String transactionId;

    private Long amountVnd;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String idempotencyKey;

    private LocalDateTime verifiedAt;

    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
