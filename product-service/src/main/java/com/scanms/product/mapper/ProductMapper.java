package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.response.ProductResponse;
import com.scanms.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public Product toEntity(CreateProductRequest request) {
        return Product.builder()
                .storeId(request.storeId())
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .material(request.material())
                .attributes(request.attributes())
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
                entity.getCategoryId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getMaterial(),
                entity.getAttributes(),
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
