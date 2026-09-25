package com.scanms.product.dto.request;

import com.scanms.product.constant.StoreApplicationStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateStoreApplicationRequest(
        @NotNull String applicantUserId,
        @NotBlank String proposedStoreName,
        String description,
        @NotNull StoreApplicationStatus status,
        String reviewedByUserId,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt
) {}

