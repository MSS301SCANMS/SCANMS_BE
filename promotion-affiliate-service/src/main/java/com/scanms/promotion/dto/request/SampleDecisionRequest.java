package com.scanms.promotion.dto.request;

import jakarta.validation.constraints.*;

public record SampleDecisionRequest(@NotNull @Min(0) Long expectedVersion,
                                    @Size(max = 1000) String response,
                                    @Size(max = 255) String carrierName,
                                    @Size(max = 255) String trackingNumber) {
}
