package com.scanms.product.service;

import com.scanms.product.entity.StoreChatMessage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Interface nghiệp vụ nhắn tin trực tiếp giữa Khách hàng và Chủ gian hàng.
 * Tuân thủ quy chuẩn Clean Architecture: tách biệt Interface và Implementation.
 */
public interface StoreChatService {

    List<Map<String, Object>> list();

    Map<String, Object> open(String storeId);

    List<StoreChatMessage> history(String id, int take, String cursor);

    StoreChatMessage send(String id, String text, String messageId);

    void read(String id);

    Map<String, Object> messageDto(StoreChatMessage message);
}
