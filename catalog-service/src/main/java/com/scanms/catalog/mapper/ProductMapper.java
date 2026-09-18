package com.scanms.catalog.mapper;

import com.scanms.catalog.dto.request.CreateProductRequest;
import com.scanms.catalog.dto.response.ProductResponse;
import com.scanms.catalog.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public Product toEntity(CreateProductRequest request) {
        return Product.builder()
                .storeId(request.storeId())
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .material(request.material())
                .sizesStock(request.sizesStock())
                .sizeGuide(request.sizeGuide())
                .imageRefs(request.imageRefs())
                .priceVnd(request.priceVnd())
                .status(request.status())
                .build();
    }

    public ProductResponse toResponse(Product entity) {
        return new ProductResponse(
                entity.getProductId(),
                entity.getStoreId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getMaterial(),
                entity.getSizesStock(),
                entity.getSizeGuide(),
                entity.getImageRefs(),
                entity.getPriceVnd(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
