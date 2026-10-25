package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import com.scanms.promotion.entity.CommissionPolicy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CommissionPolicyMapper {
    CommissionPolicy toEntity(CreateCommissionPolicyRequest request);
    CommissionPolicyResponse toResponse(CommissionPolicy entity);
}
