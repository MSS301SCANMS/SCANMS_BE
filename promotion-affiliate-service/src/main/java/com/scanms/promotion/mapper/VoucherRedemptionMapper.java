package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateVoucherRedemptionRequest;
import com.scanms.promotion.dto.response.VoucherRedemptionResponse;
import com.scanms.promotion.entity.VoucherRedemption;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VoucherRedemptionMapper {
    VoucherRedemption toEntity(CreateVoucherRedemptionRequest request);
    VoucherRedemptionResponse toResponse(VoucherRedemption entity);
}
