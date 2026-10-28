package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

import java.util.Map;

public record SearchEventRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String requestId,
                                 @Pattern(regexp = "[A-Za-z0-9_-]{16,100}") String sessionId,
                                 @NotBlank @Size(max = 200) String keyword,
                                 @Min(0) int resultCount,
                                 @Size(max = 12) Map<@Size(max = 30) String, @Size(max = 200) String> filters) {
}
