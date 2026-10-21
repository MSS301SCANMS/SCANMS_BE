package com.scanms.product.repository;

import com.scanms.product.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, String> {
    java.util.List<Store> findByOwnerUserId(String ownerUserId);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query("select s.approvalStatus, count(s) from Store s group by s.approvalStatus")
    java.util.List<Object[]> countGroupByStatus();

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from stores where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerHourSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from stores where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerDaySince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from stores where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerMonthSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);
}
