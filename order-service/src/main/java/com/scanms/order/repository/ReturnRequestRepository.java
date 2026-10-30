package com.scanms.order.repository;

import com.scanms.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import com.scanms.order.constant.ReturnRequestStatus;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, String> {
    @Query("select i.productId, sum(r.quantity) from ReturnRequest r join OrderItem i on i.orderItemId=r.orderItemId join SellerOrder s on s.sellerOrderId=i.sellerOrderId join Order o on o.orderId=s.orderId where i.productId in :ids and r.status=com.scanms.order.constant.ReturnRequestStatus.REFUNDED and s.status in (com.scanms.order.constant.SellerOrderStatus.DELIVERED, com.scanms.order.constant.SellerOrderStatus.COMPLETED) and o.status in (com.scanms.order.constant.OrderStatus.PAID, com.scanms.order.constant.OrderStatus.PROCESSING, com.scanms.order.constant.OrderStatus.COMPLETED) group by i.productId")
    List<Object[]> refundedQuantities(@Param("ids") Collection<String> productIds);

    @Query("select r from ReturnRequest r where r.orderItemId in (select i.orderItemId from OrderItem i where i.sellerOrderId in (select s.sellerOrderId from SellerOrder s where s.storeId in :storeIds)) order by r.requestedAt desc")
    List<ReturnRequest> findForStores(@Param("storeIds") Collection<String> storeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.returnRequestId=:id")
    Optional<ReturnRequest> lockById(@Param("id") String id);

    List<ReturnRequest> findByOrderItemId(String orderItemId);

    List<ReturnRequest> findByOrderItemIdAndStatusNot(String orderItemId, ReturnRequestStatus status);
}
