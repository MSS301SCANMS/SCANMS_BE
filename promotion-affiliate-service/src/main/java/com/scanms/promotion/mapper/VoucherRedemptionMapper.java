package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateVoucherRedemptionRequest;
import com.scanms.promotion.dto.response.VoucherRedemptionResponse;
import com.scanms.promotion.entity.VoucherRedemption;
import org.springframework.stereotype.Component;

@Component
public class VoucherRedemptionMapper {
    public VoucherRedemption toEntity(CreateVoucherRedemptionRequest request) {
        return VoucherRedemption.builder()
                .voucherId(request.voucherId())
                .userId(request.userId())
                .orderId(request.orderId())
                .discountAmountVnd(request.discountAmountVnd())
                .status(request.status())
                .cancelledAt(request.cancelledAt())
                .build();
    }
    public VoucherRedemptionResponse toResponse(VoucherRedemption entity) {
        return new VoucherRedemptionResponse(
                entity.getRedemptionId(),
                entity.getVoucherId(),
                entity.getUserId(),
                entity.getOrderId(),
                entity.getDiscountAmountVnd(),
                entity.getStatus(),
                entity.getRedeemedAt(),
                entity.getCancelledAt());
    }
}

