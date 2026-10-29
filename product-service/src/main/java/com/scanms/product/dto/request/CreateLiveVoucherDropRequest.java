package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateLiveVoucherDropRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String voucherId,
        @NotNull @Min(1) @Max(10000) Long quantity,
        Instant releaseAt,
        @NotNull Instant expiresAt
) {
}
