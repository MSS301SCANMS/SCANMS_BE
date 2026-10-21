package com.scanms.product.entity;

import com.scanms.product.constant.StoreStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import com.scanms.product.config.CatalogEntityListener;
import java.time.Instant;

@Entity
@Table(name = "stores", uniqueConstraints = @UniqueConstraint(name = "uk_store_owner", columnNames = "ownerUserId"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EntityListeners(CatalogEntityListener.class)
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String storeId;

    private String ownerUserId;

    private String name;

    private String description;

    private String logoRef;

    private BigDecimal ratingAverage;

    private Long ratingCount;

    private Instant lastActiveAt;

    @Enumerated(EnumType.STRING)
    private StoreStatus approvalStatus;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
