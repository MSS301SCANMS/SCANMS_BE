package com.scanms.order.repository;

import com.scanms.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    List<OrderItem> findBySellerOrderId(String sellerOrderId);
    List<OrderItem> findBySellerOrderIdIn(Collection<String> sellerOrderIds);
}
