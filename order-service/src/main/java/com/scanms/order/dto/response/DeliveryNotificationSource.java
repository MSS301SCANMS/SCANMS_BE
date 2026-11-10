package com.scanms.order.dto.response;

import java.time.LocalDateTime;

public record DeliveryNotificationSource(String sellerOrderId, String orderId, String customerId,
                                         String storeId, LocalDateTime deliveredAt, boolean read) {
}
