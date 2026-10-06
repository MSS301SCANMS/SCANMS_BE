package com.scanms.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;

@Entity
@Table(name = "product_categories", uniqueConstraints = @UniqueConstraint(name = "uk_product_category_slug", columnNames = "slug"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String categoryId;
    private String parentId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String slug;
    private String description;
    @Column(nullable = false)
    private boolean active;
    private Integer displayOrder;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
