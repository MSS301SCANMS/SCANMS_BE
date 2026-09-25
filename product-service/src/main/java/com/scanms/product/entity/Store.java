package com.scanms.product.entity;

import com.scanms.product.constant.StoreStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "stores")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
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
