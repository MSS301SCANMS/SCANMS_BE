package com.scanms.product.repository;

import com.scanms.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByStoreId(String storeId);
    List<Product> findByStoreIdIn(java.util.Collection<String> storeIds);
    boolean existsByCategoryId(String categoryId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findLockedByProductId(String productId);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    @Query("select p.status, count(p) from Product p group by p.status")
    List<Object[]> countGroupByStatus();

    @Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from products where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerHourSince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from products where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from products where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerMonthSince(@Param("from") LocalDateTime from);
}
