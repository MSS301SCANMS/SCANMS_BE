package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import com.scanms.product.entity.Store;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoreMapper {
    Store toEntity(CreateStoreRequest request);
    StoreResponse toResponse(Store entity);
}
