package com.scanms.product.repository;

import com.scanms.product.entity.Feedback;
import com.scanms.product.constant.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    boolean existsByOrderItemIdAndCustomerIdAndStatus(
            String orderItemId, String customerId, FeedbackStatus status);
}
