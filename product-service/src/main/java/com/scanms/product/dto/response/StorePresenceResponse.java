package com.scanms.product.dto.response;

import java.time.Instant;

public record StorePresenceResponse(
        String storeId, boolean isOnline, Instant lastActiveAt, Instant checkedAt) {}
