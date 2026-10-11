package com.scanms.product.repository;

import com.scanms.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
    java.util.List<Product> findByStoreId(String storeId);
    boolean existsByCategoryId(String categoryId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Product> findLockedByProductId(String productId);
}
