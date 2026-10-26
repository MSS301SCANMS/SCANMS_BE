package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateSellerSettlementRequest;
import com.scanms.payment.dto.response.SellerSettlementResponse;
import com.scanms.payment.entity.SellerSettlement;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SellerSettlementMapper {
    SellerSettlement toEntity(CreateSellerSettlementRequest request);
    SellerSettlementResponse toResponse(SellerSettlement entity);
}
