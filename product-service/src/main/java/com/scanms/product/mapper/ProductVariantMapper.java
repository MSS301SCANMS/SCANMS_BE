package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.dto.response.ProductVariantResponse;
import com.scanms.product.entity.ProductVariant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {
    ProductVariant toEntity(CreateProductVariantRequest request);
    ProductVariantResponse toResponse(ProductVariant entity);
}
