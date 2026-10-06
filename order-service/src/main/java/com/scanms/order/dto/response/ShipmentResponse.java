package com.scanms.order.dto.response;

import com.scanms.order.constant.ShipmentStatus;
import java.time.Instant;

public record ShipmentResponse(
        String shipmentId,
        String sellerOrderId,
        String carrier,
        String trackingCode,
        ShipmentStatus status,
        Instant shippedAt,
        Instant deliveredAt,
        Instant failedAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {}

