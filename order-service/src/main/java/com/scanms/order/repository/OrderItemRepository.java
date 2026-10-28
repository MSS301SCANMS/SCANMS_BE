package com.scanms.order.repository;

import com.scanms.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    @org.springframework.data.jpa.repository.Query("select i.productId, sum(i.quantity) from OrderItem i join SellerOrder s on s.sellerOrderId=i.sellerOrderId join Order o on o.orderId=s.orderId where i.productId in :ids and s.status in (com.scanms.order.constant.SellerOrderStatus.DELIVERED, com.scanms.order.constant.SellerOrderStatus.COMPLETED) and o.status in (com.scanms.order.constant.OrderStatus.PAID, com.scanms.order.constant.OrderStatus.PROCESSING, com.scanms.order.constant.OrderStatus.COMPLETED) group by i.productId")
    List<Object[]> deliveredQuantities(@org.springframework.data.repository.query.Param("ids") Collection<String> productIds);

    List<OrderItem> findBySellerOrderId(String sellerOrderId);

    List<OrderItem> findBySellerOrderIdIn(Collection<String> sellerOrderIds);

    List<OrderItem> findBySourceLivestreamId(String sourceLivestreamId);
}
