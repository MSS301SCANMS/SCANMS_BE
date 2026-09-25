package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import com.scanms.order.entity.DiscountAllocation;
import org.springframework.stereotype.Component;

@Component
public class DiscountAllocationMapper {
    public DiscountAllocation toEntity(CreateDiscountAllocationRequest request) {
        return DiscountAllocation.builder()
                .orderItemId(request.orderItemId())
                .voucherId(request.voucherId())
                .sourceType(request.sourceType())
                .fundedBy(request.fundedBy())
                .allocatedAmountVnd(request.allocatedAmountVnd())
                .ruleSnapshotJson(request.ruleSnapshotJson())
                .build();
    }

    public DiscountAllocationResponse toResponse(DiscountAllocation entity) {
        return new DiscountAllocationResponse(
                entity.getAllocationId(),
                entity.getOrderItemId(),
                entity.getVoucherId(),
                entity.getSourceType(),
                entity.getFundedBy(),
                entity.getAllocatedAmountVnd(),
                entity.getRuleSnapshotJson(),
                entity.getCreatedAt());
    }
}

