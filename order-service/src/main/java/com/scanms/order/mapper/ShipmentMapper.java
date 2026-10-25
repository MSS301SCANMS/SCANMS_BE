package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import com.scanms.order.entity.Shipment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShipmentMapper {
    Shipment toEntity(CreateShipmentRequest request);
    ShipmentResponse toResponse(Shipment entity);
}
