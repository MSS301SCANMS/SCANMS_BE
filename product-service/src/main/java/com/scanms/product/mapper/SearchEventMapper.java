package com.scanms.product.mapper;

import com.scanms.product.entity.SearchEvent;
import com.scanms.product.dto.response.SearchEventResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SearchEventMapper {
    SearchEventResponse toResponse(SearchEvent entity);
}
