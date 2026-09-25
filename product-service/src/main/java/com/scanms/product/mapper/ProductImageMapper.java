package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductImageRequest;
import com.scanms.product.dto.response.ProductImageResponse;
import com.scanms.product.entity.ProductImage;
import org.springframework.stereotype.Component;

@Component
public class ProductImageMapper {
    public ProductImage toEntity(CreateProductImageRequest request) {
        return ProductImage.builder()
                .productId(request.productId())
                .objectKey(request.objectKey())
                .imageUrl(request.imageUrl())
                .primaryImage(request.primaryImage())
                .sortOrder(request.sortOrder())
                .altText(request.altText())
                .build();
    }

    public ProductImageResponse toResponse(ProductImage entity) {
        return new ProductImageResponse(
                entity.getImageId(),
                entity.getProductId(),
                entity.getObjectKey(),
                entity.getImageUrl(),
                entity.isPrimaryImage(),
                entity.getSortOrder(),
                entity.getAltText(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

