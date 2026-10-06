package com.scanms.order.repository;

import com.scanms.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
    java.util.Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId,String idempotencyKey);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.orderId=:id")
    java.util.Optional<Order> lockById(@org.springframework.data.repository.query.Param("id") String id);
}
