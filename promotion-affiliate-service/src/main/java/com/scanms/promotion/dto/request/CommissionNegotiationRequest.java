package com.scanms.promotion.dto.request;

import jakarta.validation.constraints.*;

public record CommissionNegotiationRequest(
        @NotBlank @Pattern(regexp = "ACCEPT|REJECT|COUNTER") String action,
        @NotNull @Min(0) Long expectedVersion,
        @Min(1) @Max(3) Integer extraPoints,
        @Size(max = 1000) String note) {
}
