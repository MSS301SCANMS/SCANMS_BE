package com.scanms.order.dto.response;

import java.util.List;
import java.util.Map;

public record OrderNotificationPage(List<OrderNotificationResponse> items, long total, int page, int limit,
                                    int totalPages, long unreadCount, Map<String, Long> categoryUnreadCounts) {
}
