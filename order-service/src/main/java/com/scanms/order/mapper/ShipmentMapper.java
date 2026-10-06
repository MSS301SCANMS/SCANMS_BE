package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import com.scanms.order.entity.Shipment;
import org.springframework.stereotype.Component;

@Component
public class ShipmentMapper {
    public Shipment toEntity(CreateShipmentRequest request) {
        return Shipment.builder()
                .sellerOrderId(request.sellerOrderId())
                .carrier(request.carrier())
                .trackingCode(request.trackingCode())
                .status(request.status())
                .shippedAt(request.shippedAt())
                .deliveredAt(request.deliveredAt())
                .failedAt(request.failedAt())
                .failureReason(request.failureReason())
                .build();
    }

    public ShipmentResponse toResponse(Shipment entity) {
        return new ShipmentResponse(
                entity.getShipmentId(),
                entity.getSellerOrderId(),
                entity.getCarrier(),
                entity.getTrackingCode(),
                entity.getStatus(),
                entity.getShippedAt(),
                entity.getDeliveredAt(),
                entity.getFailedAt(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

