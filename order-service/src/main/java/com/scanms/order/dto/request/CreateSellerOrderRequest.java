package com.scanms.order.dto.request;

import com.scanms.order.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateSellerOrderRequest(
        @NotNull String orderId,
        @NotNull String storeId,
        Map<String, Object> totalsSnapshot,
        SellerOrderStatus status,
        LocalDateTime deliveredAt,
        LocalDateTime returnDeadline
) {}
