package com.scanms.product.dto.response;

import java.time.Instant;

public record SearchEventResponse(String eventId, String keyword, int resultCount, Instant createdAt) {
}
