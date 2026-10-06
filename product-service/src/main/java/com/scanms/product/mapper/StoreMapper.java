package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import com.scanms.product.entity.Store;
import org.springframework.stereotype.Component;

@Component
public class StoreMapper {
    public Store toEntity(CreateStoreRequest request) {
        return Store.builder()
                .ownerUserId(request.ownerUserId())
                .name(request.name())
                .description(request.description())
                .logoRef(request.logoRef())
                .ratingAverage(request.ratingAverage())
                .ratingCount(request.ratingCount())
                .approvalStatus(request.approvalStatus())
                .build();
    }

    public StoreResponse toResponse(Store entity) {
        return new StoreResponse(
                entity.getStoreId(),
                entity.getOwnerUserId(),
                entity.getName(),
                entity.getDescription(),
                entity.getLogoRef(),
                entity.getRatingAverage(),
                entity.getRatingCount(),
                entity.getApprovalStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
