package com.scanms.catalog.dto.response;

import com.scanms.catalog.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record StoreResponse(
        UUID storeId,
        UUID ownerUserId,
        String name,
        String description,
        StoreStatus approvalStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {}
