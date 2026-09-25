package com.scanms.order.entity;


import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "order_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderItemId;

    private String sellerOrderId;

    private String productId;

    private String variantId;

    private String sourceLivestreamId;

    private Integer quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> productSnapshot;

    private String selectedSize;

    private Long unitPrice;

    private Long grossAmountVnd;

    private Long discountAmountVnd;

    private Long netPaidAmountVnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Deprecated(forRemoval = false)
    private Map<String, Object> discountAllocations;

    private String referralLinkId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> ruleSnapshot;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
