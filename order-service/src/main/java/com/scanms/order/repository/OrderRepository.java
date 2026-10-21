package com.scanms.order.repository;

import com.scanms.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<Order> findAllByOrderByCreatedAtDesc();
    Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId=:id")
    Optional<Order> lockById(@Param("id") String id);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countGroupByStatus();

    @Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from orders where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerHourSince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from orders where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from orders where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerMonthSince(@Param("from") LocalDateTime from);
}
