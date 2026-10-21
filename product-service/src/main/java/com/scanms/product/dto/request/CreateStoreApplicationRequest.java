package com.scanms.product.dto.request;

import com.scanms.product.constant.StoreApplicationStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;

public record CreateStoreApplicationRequest(
        String applicantUserId,
        @NotBlank String proposedStoreName,
        String description,
        StoreApplicationStatus status,
        String reviewedByUserId,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt,
        @NotNull @Size(min = 12, max = 20) Map<String, Object> onboardingData
) {}

