package com.scanms.product.repository;

import com.scanms.product.entity.Feedback;
import com.scanms.product.constant.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    boolean existsByOrderItemIdAndCustomerId(String orderItemId, String customerId);
    java.util.List<Feedback> findByCustomerIdAndOrderItemIdIn(String customerId, java.util.Collection<String> ids);
    java.util.List<Feedback> findByProductIdInAndStatus(java.util.Collection<String> productIds, FeedbackStatus status);
}
