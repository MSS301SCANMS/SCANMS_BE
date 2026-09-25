package com.scanms.product.dto.response;

import com.scanms.product.constant.StoreApplicationStatus;
import java.time.Instant;

public record StoreApplicationResponse(
        String applicationId,
        String applicantUserId,
        String proposedStoreName,
        String description,
        StoreApplicationStatus status,
        String reviewedByUserId,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {}

