package com.scanms.product.repository;

import com.scanms.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    List<ProductImage> findByProductId(String productId);
    List<ProductImage> findByProductIdIn(Collection<String> productIds);
}

