package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import com.scanms.order.entity.DiscountAllocation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DiscountAllocationMapper {
    DiscountAllocation toEntity(CreateDiscountAllocationRequest request);
    DiscountAllocationResponse toResponse(DiscountAllocation entity);
}
