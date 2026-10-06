package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import com.scanms.order.entity.ReturnRequest;
import org.springframework.stereotype.Component;

@Component
public class ReturnRequestMapper {
    public ReturnRequest toEntity(CreateReturnRequest request) {
        return ReturnRequest.builder()
                .orderItemId(request.orderItemId())
                .quantity(request.quantity())
                .reason(request.reason())
                .evidenceRefs(request.evidenceRefs())
                .status(request.status())
                .decisionReason(request.decisionReason())
                .refundAmountVnd(request.refundAmount())
                .refundReference(request.refundReference())
                .requestedAt(request.requestedAt())
                .resolvedAt(request.resolvedAt())
                .build();
    }

    public ReturnRequestResponse toResponse(ReturnRequest entity) {
        return new ReturnRequestResponse(
                entity.getReturnRequestId(),
                entity.getOrderItemId(),
                entity.getQuantity(),
                entity.getReason(),
                entity.getEvidenceRefs(),
                entity.getStatus(),
                entity.getDecisionReason(),
                entity.getRefundAmountVnd(),
                entity.getRefundReference(),
                entity.getRequestedAt(),
                entity.getResolvedAt());
    }
}
