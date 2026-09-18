package com.scanms.catalog.mapper;

import com.scanms.catalog.dto.request.CreateStoreRequest;
import com.scanms.catalog.dto.response.StoreResponse;
import com.scanms.catalog.entity.Store;
import org.springframework.stereotype.Component;

@Component
public class StoreMapper {
    public Store toEntity(CreateStoreRequest request) {
        return Store.builder()
                .ownerUserId(request.ownerUserId())
                .name(request.name())
                .description(request.description())
                .approvalStatus(request.approvalStatus())
                .build();
    }

    public StoreResponse toResponse(Store entity) {
        return new StoreResponse(
                entity.getStoreId(),
                entity.getOwnerUserId(),
                entity.getName(),
                entity.getDescription(),
                entity.getApprovalStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
