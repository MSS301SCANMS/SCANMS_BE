package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.FeeConfigResponse;
import com.scanms.payment.entity.FeeConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FeeConfigMapper {
    FeeConfig toEntity(CreateFeeConfigRequest request);
    FeeConfigResponse toResponse(FeeConfig entity);
}
