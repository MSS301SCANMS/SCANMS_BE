package com.scanms.promotion.entity;

import com.scanms.promotion.constant.VoucherIssuerType;
import com.scanms.promotion.constant.VoucherDiscountType;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "vouchers")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID voucherId;

    private String code;

    @Enumerated(EnumType.STRING)
    private VoucherIssuerType issuerType;

    private UUID storeId;

    @Enumerated(EnumType.STRING)
    private VoucherDiscountType discountType;

    private BigDecimal value;

    private Long cap;

    private Long minimumSubtotal;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> limits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> funding;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
