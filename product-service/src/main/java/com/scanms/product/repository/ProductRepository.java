package com.scanms.product.repository;

import com.scanms.product.constant.ProductStatus;
import com.scanms.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByStoreId(String storeId);
    List<Product> findByStoreIdIn(Collection<String> storeIds);
    Page<Product> findByStoreIdIn(Collection<String> storeIds, Pageable pageable);
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    Page<Product> findByStoreIdInAndStatus(Collection<String> storeIds, ProductStatus status, Pageable pageable);
    boolean existsByCategoryId(String categoryId);

    /** :q must be non-null lowercase fragment (no leading/trailing %). Avoids PG lower(bytea) on null binds. */
    @Query("""
        select p from Product p
        where lower(p.name) like concat('%', :q, '%')
           or lower(coalesce(p.description, '')) like concat('%', :q, '%')
           or lower(coalesce(p.category, '')) like concat('%', :q, '%')
        """)
    Page<Product> searchAllByText(@Param("q") String q, Pageable pageable);

    @Query("""
        select p from Product p
        where p.status = :status
          and (
            lower(p.name) like concat('%', :q, '%')
            or lower(coalesce(p.description, '')) like concat('%', :q, '%')
            or lower(coalesce(p.category, '')) like concat('%', :q, '%')
          )
        """)
    Page<Product> searchAllByStatusAndText(
            @Param("status") ProductStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
        select p from Product p
        where p.storeId in :storeIds
          and (
            lower(p.name) like concat('%', :q, '%')
            or lower(coalesce(p.description, '')) like concat('%', :q, '%')
            or lower(coalesce(p.category, '')) like concat('%', :q, '%')
          )
        """)
    Page<Product> searchByStoresAndText(
            @Param("storeIds") Collection<String> storeIds,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
        select p from Product p
        where p.storeId in :storeIds
          and p.status = :status
          and (
            lower(p.name) like concat('%', :q, '%')
            or lower(coalesce(p.description, '')) like concat('%', :q, '%')
            or lower(coalesce(p.category, '')) like concat('%', :q, '%')
          )
        """)
    Page<Product> searchByStoresStatusAndText(
            @Param("storeIds") Collection<String> storeIds,
            @Param("status") ProductStatus status,
            @Param("q") String q,
            Pageable pageable);
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
