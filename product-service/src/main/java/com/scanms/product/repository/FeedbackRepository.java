package com.scanms.product.repository;

import com.scanms.product.entity.Feedback;
import com.scanms.product.constant.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    boolean existsByOrderItemIdAndCustomerId(String orderItemId, String customerId);
    List<Feedback> findByCustomerIdAndOrderItemIdIn(String customerId, Collection<String> ids);
    List<Feedback> findByProductIdInAndStatus(Collection<String> productIds, FeedbackStatus status);
}
