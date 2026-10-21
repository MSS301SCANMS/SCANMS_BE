package com.scanms.order.repository;

import com.scanms.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import com.scanms.order.constant.ReturnRequestStatus;
import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, String> {
    @org.springframework.data.jpa.repository.Query("select r from ReturnRequest r where r.orderItemId in (select i.orderItemId from OrderItem i where i.sellerOrderId in (select s.sellerOrderId from SellerOrder s where s.storeId in :storeIds)) order by r.requestedAt desc")
    List<ReturnRequest> findForStores(@org.springframework.data.repository.query.Param("storeIds") java.util.Collection<String> storeIds);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ReturnRequest r where r.returnRequestId=:id")
    java.util.Optional<ReturnRequest> lockById(@org.springframework.data.repository.query.Param("id") String id);
    List<ReturnRequest> findByOrderItemId(String orderItemId);
    List<ReturnRequest> findByOrderItemIdAndStatusNot(String orderItemId, ReturnRequestStatus status);
}
