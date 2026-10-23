package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateProductCategoryRequest;
import com.scanms.product.dto.response.ProductCategoryResponse;
import com.scanms.product.entity.ProductCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {
    ProductCategory toEntity(CreateProductCategoryRequest request);
    ProductCategoryResponse toResponse(ProductCategory entity);
}
