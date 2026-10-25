package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.dto.response.StoreApplicationResponse;
import com.scanms.product.entity.StoreApplication;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoreApplicationMapper {
    StoreApplication toEntity(CreateStoreApplicationRequest request);
    StoreApplicationResponse toResponse(StoreApplication entity);
}
