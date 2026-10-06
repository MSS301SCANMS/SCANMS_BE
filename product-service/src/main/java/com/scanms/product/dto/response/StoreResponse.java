package com.scanms.product.dto.response;

import com.scanms.product.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record StoreResponse(
        String storeId,
        String ownerUserId,
        String name,
        String description,
        String logoRef,
        BigDecimal ratingAverage,
        Long ratingCount,
        StoreStatus approvalStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {}
