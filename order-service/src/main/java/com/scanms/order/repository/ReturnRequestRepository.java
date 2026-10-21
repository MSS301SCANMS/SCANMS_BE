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
    @Query("select r from ReturnRequest r where r.orderItemId in (select i.orderItemId from OrderItem i where i.sellerOrderId in (select s.sellerOrderId from SellerOrder s where s.storeId in :storeIds)) order by r.requestedAt desc")
    List<ReturnRequest> findForStores(@Param("storeIds") Collection<String> storeIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.returnRequestId=:id")
    Optional<ReturnRequest> lockById(@Param("id") String id);
    List<ReturnRequest> findByOrderItemId(String orderItemId);
    List<ReturnRequest> findByOrderItemIdAndStatusNot(String orderItemId, ReturnRequestStatus status);
}
