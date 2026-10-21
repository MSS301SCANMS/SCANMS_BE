package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import com.scanms.promotion.entity.ReferralLink;
import org.springframework.stereotype.Component;

@Component
public class ReferralLinkMapper {
    public ReferralLink toEntity(CreateReferralLinkRequest request) {
        return ReferralLink.builder()
                .collaboratorId(request.collaboratorId())
                .productId(request.productId())
                .livestreamId(request.livestreamId())
                .token(request.token())
                .status(request.status())
                .expiresAt(request.expiresAt())
                .label(request.label())
                .channel(request.channel())
                .build();
    }

    public ReferralLinkResponse toResponse(ReferralLink entity) {
        return new ReferralLinkResponse(
                entity.getReferralLinkId(),
                entity.getCollaboratorId(),
                entity.getProductId(),
                entity.getLivestreamId(),
                entity.getToken(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getLabel(),
                entity.getChannel());
    }
}
