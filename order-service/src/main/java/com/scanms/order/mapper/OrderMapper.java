package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.OrderResponse;
import com.scanms.order.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    public Order toEntity(CreateOrderRequest request) {
        return Order.builder()
                .customerId(request.customerId())
                .idempotencyKey(request.idempotencyKey())
                .payableVnd(request.payableVnd())
                .currency(request.currency())
                .shippingSnapshot(request.shippingSnapshot())
                .status(request.status())
                .sagaState(request.sagaState())
                .expiresAt(request.expiresAt())
                .build();
    }

    public OrderResponse toResponse(Order entity) {
        return new OrderResponse(
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getIdempotencyKey(),
                entity.getPayableVnd(),
                entity.getCurrency(),
                entity.getShippingSnapshot(),
                entity.getStatus(),
                entity.getSagaState(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
