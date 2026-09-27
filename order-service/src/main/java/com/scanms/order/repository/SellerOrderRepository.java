package com.scanms.order.repository;

import com.scanms.order.entity.SellerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import com.scanms.order.constant.SellerOrderStatus;
import java.util.List;

public interface SellerOrderRepository extends JpaRepository<SellerOrder, String> {
    List<SellerOrder> findByOrderId(String orderId);
    List<SellerOrder> findByStoreIdOrderByCreatedAtDesc(String storeId);
    List<SellerOrder> findByStoreIdAndStatusOrderByCreatedAtDesc(String storeId, SellerOrderStatus status);
}
