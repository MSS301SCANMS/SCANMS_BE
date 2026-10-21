package com.scanms.product.repository;

import com.scanms.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
    java.util.List<Product> findByStoreId(String storeId);
    boolean existsByCategoryId(String categoryId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Product> findLockedByProductId(String productId);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query("select p.status, count(p) from Product p group by p.status")
    java.util.List<Object[]> countGroupByStatus();

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from products where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerHourSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from products where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerDaySince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from products where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerMonthSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);
}
