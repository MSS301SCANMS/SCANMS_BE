package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import java.util.*;

public interface ReferralLinkService {
    ReferralLinkResponse create(CreateReferralLinkRequest request);
    ReferralLinkResponse getById(UUID id);
    List<ReferralLinkResponse> findAll();
}
