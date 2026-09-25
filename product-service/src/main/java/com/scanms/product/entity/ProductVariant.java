package com.scanms.product.entity;

import com.scanms.product.constant.ProductVariantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;

@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(name = "uk_product_variant_sku", columnNames = "sku"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String variantId;
    @Column(nullable = false)
    private String productId;
    @Column(nullable = false)
    private String sku;
    private String size;
    private String color;
    @Column(nullable = false)
    private Long priceVnd;
    @Column(nullable = false)
    private Integer stockQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ProductVariantStatus status;
    @Version
    private Long version;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
