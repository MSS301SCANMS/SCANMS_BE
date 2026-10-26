package com.scanms.product.service;

import com.scanms.product.dto.response.StorePresenceResponse;
import java.util.List;

public interface StorePresenceService {
    List<StorePresenceResponse> heartbeat();

    StorePresenceResponse publicPresence(String storeId);
}
