package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import java.util.*;

public interface ReferralLinkService {
    ReferralLinkResponse create(CreateReferralLinkRequest request);
    ReferralLinkResponse getById(String id);
    List<ReferralLinkResponse> findAll();
}
