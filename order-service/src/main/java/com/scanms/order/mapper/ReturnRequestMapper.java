package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import com.scanms.order.entity.ReturnRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReturnRequestMapper {
    // request.refundAmount() ánh xạ sang entity.refundAmountVnd (tên khác nhau giữa DTO và entity).
    @Mapping(target = "refundAmountVnd", source = "refundAmount")
    ReturnRequest toEntity(CreateReturnRequest request);

    ReturnRequestResponse toResponse(ReturnRequest entity);
}
