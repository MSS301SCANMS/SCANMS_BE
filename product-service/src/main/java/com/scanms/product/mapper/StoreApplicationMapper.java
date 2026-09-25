package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.dto.response.StoreApplicationResponse;
import com.scanms.product.entity.StoreApplication;
import org.springframework.stereotype.Component;

@Component
public class StoreApplicationMapper {
    public StoreApplication toEntity(CreateStoreApplicationRequest request) {
        return StoreApplication.builder()
                .applicantUserId(request.applicantUserId())
                .proposedStoreName(request.proposedStoreName())
                .description(request.description())
                .status(request.status())
                .reviewedByUserId(request.reviewedByUserId())
                .reviewNote(request.reviewNote())
                .submittedAt(request.submittedAt())
                .reviewedAt(request.reviewedAt())
                .build();
    }

    public StoreApplicationResponse toResponse(StoreApplication entity) {
        return new StoreApplicationResponse(
                entity.getApplicationId(),
                entity.getApplicantUserId(),
                entity.getProposedStoreName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getReviewedByUserId(),
                entity.getReviewNote(),
                entity.getSubmittedAt(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

