package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import com.scanms.product.entity.ProductVariant;
import org.springframework.stereotype.Component;

@Component
public class ProductVariantMapper {
    public ProductVariant toEntity(CreateProductVariantRequest request) {
        return ProductVariant.builder()
                .productId(request.productId())
                .sku(request.sku())
                .size(request.size())
                .color(request.color())
                .priceVnd(request.priceVnd())
                .stockQuantity(request.stockQuantity())
                .status(request.status())
                .build();
    }

    public ProductVariantResponse toResponse(ProductVariant entity) {
        return new ProductVariantResponse(
                entity.getVariantId(),
                entity.getProductId(),
                entity.getSku(),
                entity.getSize(),
                entity.getColor(),
                entity.getPriceVnd(),
                entity.getStockQuantity(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

