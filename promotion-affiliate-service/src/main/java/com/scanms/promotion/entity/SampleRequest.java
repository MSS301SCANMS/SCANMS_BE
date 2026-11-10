package com.scanms.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "sample_requests", indexes = @Index(name = "idx_sample_participant", columnList = "collaboratorId,productId,status"))
@Getter
@Setter
public class SampleRequest {
    @Id
    private String id;
    @Column(nullable = false)
    private String collaboratorId;
    @Column(nullable = false)
    private String customerId;
    @Column(nullable = false)
    private String storeId;
    @Column(nullable = false)
    private String productId;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, length = 2000)
    private String note;
    @Column(length = 1000)
    private String response;
    @Column(nullable = false)
    private String recipientName;
    @Column(nullable = false)
    private String recipientPhone;
    @Column(nullable = false, length = 2000)
    private String shippingAddress;
    private String carrier;
    private String trackingCode;
    private Instant createdAt;
    private Instant shippedAt;
    private Instant receivedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> product;
    @Version
    private Long version;
}
