package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import com.scanms.promotion.entity.ReferralLink;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReferralLinkMapper {
    ReferralLink toEntity(CreateReferralLinkRequest request);
    ReferralLinkResponse toResponse(ReferralLink entity);
}
