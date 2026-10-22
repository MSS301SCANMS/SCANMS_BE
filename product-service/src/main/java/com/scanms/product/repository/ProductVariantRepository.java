package com.scanms.product.repository;

import com.scanms.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    java.util.List<ProductVariant> findByProductIdIn(java.util.Collection<String> productIds);
}

