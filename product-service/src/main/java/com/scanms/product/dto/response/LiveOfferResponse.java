package com.scanms.product.dto.response;

import java.time.Instant;

public record LiveOfferResponse(
        String id, String productId, String variantId, Long priceVnd,
        Long quantityLimit, Long reservedQuantity, Instant startsAt, Instant endsAt, String status
) {}
