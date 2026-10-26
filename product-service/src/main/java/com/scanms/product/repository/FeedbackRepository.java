package com.scanms.product.repository;

import com.scanms.product.entity.Feedback;
import com.scanms.product.constant.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {
    interface ProductRating {
        String getProductId();
        Double getAverageRating();
        long getReviewCount();
    }

    @Query("select f.productId as productId, avg(f.rating) as averageRating, count(f) as reviewCount "
            + "from Feedback f where f.productId in :ids and f.status = :status "
            + "and f.rating between 1 and 5 and f.orderItemId is not null and f.customerId is not null "
            + "group by f.productId")
    List<ProductRating> summarizeProducts(@Param("ids") Collection<String> productIds, @Param("status") FeedbackStatus status);
    boolean existsByOrderItemIdAndCustomerId(String orderItemId, String customerId);
    List<Feedback> findByCustomerIdAndOrderItemIdIn(String customerId, Collection<String> ids);
    List<Feedback> findByProductIdInAndStatus(Collection<String> productIds, FeedbackStatus status);
}
