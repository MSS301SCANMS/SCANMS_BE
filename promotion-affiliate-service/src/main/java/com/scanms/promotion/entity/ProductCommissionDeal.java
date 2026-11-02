package com.scanms.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "product_commission_deals", indexes = @Index(name = "idx_product_commission_deal_target", columnList = "collaboratorId,productId,approvedAt"))
@Getter
@Setter
public class ProductCommissionDeal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(nullable = false)
    private String collaboratorId;
    @Column(nullable = false)
    private String storeId;
    @Column(nullable = false)
    private String productId;
    @Column(nullable = false)
    private String productName;
    private String imageUrl;
    private String storeName;
    private String collaboratorName;
    @Column(nullable = false)
    private Integer extraPoints;
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal baseRate;
    @Column(precision = 10, scale = 6)
    private BigDecimal totalRate;
    private String basePolicyId;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(length = 1000)
    private String response;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant approvedAt;
    private Instant endedAt;
    private Instant respondedAt;
    private String proposedByUserId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> negotiationHistory;
    @Version
    private Long version;
}
