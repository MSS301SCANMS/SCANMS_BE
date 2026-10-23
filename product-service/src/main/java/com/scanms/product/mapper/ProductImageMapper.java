package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductImageRequest;
import com.scanms.product.dto.response.ProductImageResponse;
import com.scanms.product.entity.ProductImage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {
    ProductImage toEntity(CreateProductImageRequest request);
    ProductImageResponse toResponse(ProductImage entity);
}
