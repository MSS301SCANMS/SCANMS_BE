package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import com.scanms.product.entity.Feedback;
import org.springframework.stereotype.Component;

@Component
public class FeedbackMapper {
    public Feedback toEntity(CreateFeedbackRequest request) {
        return Feedback.builder()
                .productId(request.productId())
                .orderItemId(request.orderItemId())
                .customerId(request.customerId())
                .rating(request.rating())
                .comment(request.comment())
                .imageRefs(request.imageRefs())
                .status(request.status())
                .build();
    }

    public FeedbackResponse toResponse(Feedback entity) {
        return new FeedbackResponse(
                entity.getFeedbackId(),
                entity.getProductId(),
                entity.getOrderItemId(),
                entity.getCustomerId(),
                entity.getRating(),
                entity.getComment(),
                entity.getImageRefs(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

