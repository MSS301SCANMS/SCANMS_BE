package com.scanms.catalog.dto.request;

import com.scanms.catalog.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateStoreRequest(
        @NotNull UUID ownerUserId,
        @NotBlank String name,
        String description,
        StoreStatus approvalStatus
) {}
