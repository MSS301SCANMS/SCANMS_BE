package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateCommissionRequest;
import com.scanms.promotion.dto.response.CommissionResponse;
import com.scanms.promotion.entity.Commission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommissionMapper {
    // Tên field request khác entity: basisVnd->basisAmountVnd, rate->rateSnapshot, amountVnd->commissionAmountVnd.
    @Mapping(target = "basisAmountVnd", source = "basisVnd")
    @Mapping(target = "rateSnapshot", source = "rate")
    @Mapping(target = "commissionAmountVnd", source = "amountVnd")
    Commission toEntity(CreateCommissionRequest request);

    CommissionResponse toResponse(Commission entity);
}
