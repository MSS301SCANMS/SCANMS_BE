package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

public record LiveBroadcastHeartbeat(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String publisherId,
                                     @Min(1) long epoch, @Min(1) long sequence,
                                     @NotNull @Pattern(regexp = "LIVE|RECONNECTING") String state) {
}
