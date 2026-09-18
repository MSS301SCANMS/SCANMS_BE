package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
import com.scanms.order.entity.SellerOrder;
import org.springframework.stereotype.Component;

@Component
public class SellerOrderMapper {
    public SellerOrder toEntity(CreateSellerOrderRequest request) {
        return SellerOrder.builder()
                .orderId(request.orderId())
                .storeId(request.storeId())
                .totalsSnapshot(request.totalsSnapshot())
                .status(request.status())
                .deliveredAt(request.deliveredAt())
                .returnDeadline(request.returnDeadline())
                .build();
    }

    public SellerOrderResponse toResponse(SellerOrder entity) {
        return new SellerOrderResponse(
                entity.getSellerOrderId(),
                entity.getOrderId(),
                entity.getStoreId(),
                entity.getTotalsSnapshot(),
                entity.getStatus(),
                entity.getDeliveredAt(),
                entity.getReturnDeadline(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
