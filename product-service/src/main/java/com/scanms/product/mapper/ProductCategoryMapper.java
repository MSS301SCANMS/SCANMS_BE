package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductCategoryRequest;
import com.scanms.product.dto.response.ProductCategoryResponse;
import com.scanms.product.entity.ProductCategory;
import org.springframework.stereotype.Component;

@Component
public class ProductCategoryMapper {
    public ProductCategory toEntity(CreateProductCategoryRequest request) {
        return ProductCategory.builder()
                .parentId(request.parentId())
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .active(request.active())
                .displayOrder(request.displayOrder())
                .build();
    }

    public ProductCategoryResponse toResponse(ProductCategory entity) {
        return new ProductCategoryResponse(
                entity.getCategoryId(),
                entity.getParentId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.isActive(),
                entity.getDisplayOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

