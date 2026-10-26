package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
import com.scanms.order.entity.SellerOrder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SellerOrderMapper {
    SellerOrder toEntity(CreateSellerOrderRequest request);
    SellerOrderResponse toResponse(SellerOrder entity);
}
