package com.scanms.promotion.service;

import java.util.List;
import java.util.Map;

public interface CollaborationChatService {
    Map<String, Object> resource(String type, String id);

    Map<String, Object> partner(String storeId, String userId);

    List<Map<String, Object>> partners(String storeId);
}
