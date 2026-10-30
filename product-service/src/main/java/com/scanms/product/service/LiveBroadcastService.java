package com.scanms.product.service;

import com.scanms.product.dto.request.LiveBroadcastHeartbeat;

import java.util.Map;

public interface LiveBroadcastService {
    Map<String, Object> heartbeat(String liveId, LiveBroadcastHeartbeat input, String identitySubject);
}
