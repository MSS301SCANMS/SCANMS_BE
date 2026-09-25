package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import com.scanms.promotion.entity.CommissionPolicy;
import org.springframework.stereotype.Component;

@Component
public class CommissionPolicyMapper {
    public CommissionPolicy toEntity(CreateCommissionPolicyRequest request) {
        return CommissionPolicy.builder()
                .policyName(request.policyName())
                .scopeType(request.scopeType())
                .storeId(request.storeId())
                .productId(request.productId())
                .livestreamId(request.livestreamId())
                .collaboratorId(request.collaboratorId())
                .commissionRate(request.commissionRate())
                .returnWindowDays(request.returnWindowDays())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .status(request.status())
                .priority(request.priority())
                .build();
    }
    public CommissionPolicyResponse toResponse(CommissionPolicy entity) {
        return new CommissionPolicyResponse(
                entity.getPolicyId(),
                entity.getPolicyName(),
                entity.getScopeType(),
                entity.getStoreId(),
                entity.getProductId(),
                entity.getLivestreamId(),
                entity.getCollaboratorId(),
                entity.getCommissionRate(),
                entity.getReturnWindowDays(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

