package com.scanms.order.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record OrderNotificationResponse(String id, String userId, String title, String message, String type,
                                         Map<String, Object> data, @JsonProperty("isRead") boolean isRead, Instant createdAt) {
}
