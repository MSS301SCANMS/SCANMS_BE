package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.FeeConfigResponse;
import com.scanms.payment.entity.FeeConfig;
import org.springframework.stereotype.Component;

@Component
public class FeeConfigMapper {
    public FeeConfig toEntity(CreateFeeConfigRequest request) {
        return FeeConfig.builder()
                .feeType(request.feeType())
                .calculationType(request.calculationType())
                .ratePercent(request.ratePercent())
                .fixedAmountVnd(request.fixedAmountVnd())
                .minFeeVnd(request.minFeeVnd())
                .maxFeeVnd(request.maxFeeVnd())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .status(request.status())
                .build();
    }
    public FeeConfigResponse toResponse(FeeConfig entity) {
        return new FeeConfigResponse(
                entity.getFeeConfigId(),
                entity.getFeeType(),
                entity.getCalculationType(),
                entity.getRatePercent(),
                entity.getFixedAmountVnd(),
                entity.getMinFeeVnd(),
                entity.getMaxFeeVnd(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

