package com.scanms.order.entity;

import com.scanms.order.constant.SellerOrderStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "seller_orders")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SellerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sellerOrderId;

    private UUID orderId;

    private UUID storeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> totalsSnapshot;

    @Enumerated(EnumType.STRING)
    private SellerOrderStatus status;

    private LocalDateTime deliveredAt;

    private LocalDateTime returnDeadline;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
