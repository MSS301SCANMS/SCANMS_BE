package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.entity.CollaboratorProfile;
import org.springframework.stereotype.Component;

@Component
public class CollaboratorProfileMapper {
    public CollaboratorProfile toEntity(ApplyCollaboratorRequest request) {
        return CollaboratorProfile.builder()
                .userId(request.userId())
                .approvalStatus(request.approvalStatus())
                .joinedAt(request.joinedAt())
                .policyVersion(request.policyVersion())
                .build();
    }

    public CollaboratorProfileResponse toResponse(CollaboratorProfile entity) {
        return new CollaboratorProfileResponse(
                entity.getCollaboratorId(),
                entity.getUserId(),
                entity.getApprovalStatus(),
                entity.getJoinedAt(),
                entity.getPolicyVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
