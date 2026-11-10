package com.scanms.product.repository;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.storeId=:id")
    Optional<Store> lockById(@Param("id") String id);

    // Heartbeats must not increment Store.version or change settings' updatedAt.
    @Modifying
    @Query("update Store s set s.lastActiveAt=:now where s.ownerUserId=:owner and s.approvalStatus=:status and (s.lastActiveAt is null or s.lastActiveAt<:now)")
    int touchPresence(@Param("owner") String owner,
                      @Param("status") StoreStatus status,
                      @Param("now") Instant now);

    List<Store> findByOwnerUserId(String ownerUserId);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    @Query("select s.approvalStatus, count(s) from Store s group by s.approvalStatus")
    List<Object[]> countGroupByStatus();

    @Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from stores where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerHourSince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from stores where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from stores where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerMonthSince(@Param("from") LocalDateTime from);
}
