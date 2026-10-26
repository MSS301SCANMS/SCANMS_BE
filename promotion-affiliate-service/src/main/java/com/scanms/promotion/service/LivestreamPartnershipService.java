package com.scanms.promotion.service;

import java.util.Map;

public interface LivestreamPartnershipService {
    Map<String, Object> verify(String storeId, String collaboratorId);
}
