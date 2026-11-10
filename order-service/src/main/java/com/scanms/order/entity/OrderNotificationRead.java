package com.scanms.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "order_notification_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_notice_reader", columnNames = {"userId", "sellerOrderId"}),
        indexes = @Index(name = "idx_delivery_notice_reader", columnList = "userId"))
@Getter
@Setter
public class OrderNotificationRead {
    @Id
    private String id;
    @Column(nullable = false)
    private String userId;
    @Column(nullable = false)
    private String sellerOrderId;
    @Column(nullable = false)
    private Instant readAt;
}
