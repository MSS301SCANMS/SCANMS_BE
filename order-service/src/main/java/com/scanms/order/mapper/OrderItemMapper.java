package com.scanms.order.mapper;

import com.scanms.order.dto.request.CreateOrderItemRequest;
import com.scanms.order.dto.response.OrderItemResponse;
import com.scanms.order.entity.OrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderItemMapper {
    public OrderItem toEntity(CreateOrderItemRequest request) {
        return OrderItem.builder()
                .sellerOrderId(request.sellerOrderId())
                .productId(request.productId())
                .variantId(request.variantId())
                .sourceLivestreamId(request.sourceLivestreamId())
                .quantity(request.quantity())
                .productSnapshot(request.productSnapshot())
                .selectedSize(request.selectedSize())
                .unitPrice(request.unitPrice())
                .grossAmountVnd(request.grossAmountVnd())
                .discountAmountVnd(request.discountAmountVnd())
                .netPaidAmountVnd(request.netPaidAmountVnd())
                .discountAllocations(request.discountAllocations())
                .referralLinkId(request.referralLinkId())
                .ruleSnapshot(request.ruleSnapshot())
                .build();
    }

    public OrderItemResponse toResponse(OrderItem entity) {
        return new OrderItemResponse(
                entity.getOrderItemId(),
                entity.getSellerOrderId(),
                entity.getProductId(),
                entity.getVariantId(),
                entity.getSourceLivestreamId(),
                entity.getQuantity(),
                entity.getProductSnapshot(),
                entity.getSelectedSize(),
                entity.getUnitPrice(),
                entity.getGrossAmountVnd(),
                entity.getDiscountAmountVnd(),
                entity.getNetPaidAmountVnd(),
                entity.getDiscountAllocations(),
                entity.getReferralLinkId(),
                entity.getRuleSnapshot(),
                entity.getCreatedAt());
    }
}
