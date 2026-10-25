package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.entity.CollaboratorProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CollaboratorProfileMapper {
    CollaboratorProfile toEntity(ApplyCollaboratorRequest request);
    CollaboratorProfileResponse toResponse(CollaboratorProfile entity);
}
