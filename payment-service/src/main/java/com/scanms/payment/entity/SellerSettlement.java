package com.scanms.payment.entity;

import com.scanms.payment.constant.SettlementStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "seller_settlements")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SellerSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String settlementId;

    private String storeId;

    private String bankAccountId;

    private String walletId;

    private String walletTransactionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> destinationSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> orderRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> breakdown;

    private Long netAmountVnd;

    @Enumerated(EnumType.STRING)
    private SettlementStatus status;

    private String transferReference;

    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
