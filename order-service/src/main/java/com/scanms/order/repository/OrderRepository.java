package com.scanms.order.repository;

import com.scanms.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<Order> findAllByOrderByCreatedAtDesc();
}
