package com.scanms.order.repository;

import com.scanms.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<Order> findAllByOrderByCreatedAtDesc();
    Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.orderId=:id")
    Optional<Order> lockById(@org.springframework.data.repository.query.Param("id") String id);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query("select o.status, count(o) from Order o group by o.status")
    java.util.List<Object[]> countGroupByStatus();

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from orders where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerHourSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from orders where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerDaySince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from orders where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerMonthSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);
}
