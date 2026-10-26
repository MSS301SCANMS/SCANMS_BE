package com.scanms.product.repository;

import com.scanms.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    List<ProductVariant> findByProductIdIn(Collection<String> productIds);
}

