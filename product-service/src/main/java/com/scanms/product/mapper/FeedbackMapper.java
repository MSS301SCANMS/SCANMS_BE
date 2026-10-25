package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import com.scanms.product.entity.Feedback;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FeedbackMapper {
    Feedback toEntity(CreateFeedbackRequest request);
    FeedbackResponse toResponse(Feedback entity);
}
