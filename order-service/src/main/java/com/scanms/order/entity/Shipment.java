package com.scanms.order.entity;

import com.scanms.order.constant.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;

@Entity
@Table(name = "shipments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String shipmentId;
    @Column(nullable = false)
    private String sellerOrderId;
    private String carrier;
    private String trackingCode;
    /** Server assigned. Legacy shipments are LIVE; only TEST may be simulated. */
    private String environment;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ShipmentStatus status;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant failedAt;
    private String failureReason;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

