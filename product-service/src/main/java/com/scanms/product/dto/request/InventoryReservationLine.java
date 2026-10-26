package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

public record InventoryReservationLine(
        @NotBlank String productId,
        String variantId,
        @Min(1) @Max(1000) int quantity,
        String sourceLivestreamId) {
    public InventoryReservationLine(String productId, String variantId, int quantity) {
        this(productId, variantId, quantity, null);
    }
}
