package com.scanms.order.repository;

import com.scanms.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import com.scanms.order.constant.ReturnRequestStatus;
import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, String> {
    List<ReturnRequest> findByOrderItemId(String orderItemId);
    List<ReturnRequest> findByOrderItemIdAndStatusNot(String orderItemId, ReturnRequestStatus status);
}
