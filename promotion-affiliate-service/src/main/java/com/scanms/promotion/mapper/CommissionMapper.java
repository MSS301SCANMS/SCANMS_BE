package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateCommissionRequest;
import com.scanms.promotion.dto.response.CommissionResponse;
import com.scanms.promotion.entity.Commission;
import org.springframework.stereotype.Component;

@Component
public class CommissionMapper {
    public Commission toEntity(CreateCommissionRequest request) {
        return Commission.builder()
                .orderItemId(request.orderItemId())
                .collaboratorId(request.collaboratorId())
                .referralLinkId(request.referralLinkId())
                .basisVnd(request.basisVnd())
                .rate(request.rate())
                .amountVnd(request.amountVnd())
                .status(request.status())
                .adjustmentHistory(request.adjustmentHistory())
                .transferReference(request.transferReference())
                .paidAt(request.paidAt())
                .build();
    }

    public CommissionResponse toResponse(Commission entity) {
        return new CommissionResponse(
                entity.getCommissionId(),
                entity.getOrderItemId(),
                entity.getCollaboratorId(),
                entity.getReferralLinkId(),
                entity.getBasisVnd(),
                entity.getRate(),
                entity.getAmountVnd(),
                entity.getStatus(),
                entity.getAdjustmentHistory(),
                entity.getTransferReference(),
                entity.getPaidAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
