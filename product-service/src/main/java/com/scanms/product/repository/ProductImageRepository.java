package com.scanms.product.repository;

import com.scanms.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    java.util.List<ProductImage> findByProductId(String productId);
}

