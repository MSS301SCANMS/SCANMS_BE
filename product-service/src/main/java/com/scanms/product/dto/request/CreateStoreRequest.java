package com.scanms.product.dto.request;

import com.scanms.product.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateStoreRequest(
        @NotNull String ownerUserId,
        @NotBlank String name,
        String description,
        String logoRef,
        @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal ratingAverage,
        @PositiveOrZero Long ratingCount,
        StoreStatus approvalStatus
) {}
