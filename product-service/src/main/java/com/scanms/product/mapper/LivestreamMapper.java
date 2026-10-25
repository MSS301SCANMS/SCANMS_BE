package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.entity.Livestream;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LivestreamMapper {
    Livestream toEntity(CreateLivestreamRequest request);
    LivestreamResponse toResponse(Livestream entity);
}
