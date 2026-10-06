package com.scanms.order.dto.request;

import com.scanms.order.constant.ShipmentStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateShipmentRequest(
        @NotNull String sellerOrderId,
        String carrier,
        String trackingCode,
        @NotNull ShipmentStatus status,
        Instant shippedAt,
        Instant deliveredAt,
        Instant failedAt,
        String failureReason
) {}

