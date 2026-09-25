package com.scanms.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;

@Entity
@Table(name = "product_images")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String imageId;
    @Column(nullable = false)
    private String productId;
    @Column(nullable = false)
    private String objectKey;
    @Column(nullable = false)
    private String imageUrl;
    @Column(nullable = false)
    private boolean primaryImage;
    private Integer sortOrder;
    private String altText;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

