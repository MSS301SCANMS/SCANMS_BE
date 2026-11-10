package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;

public record InventoryReservationLine(
        @NotBlank String productId,
        String variantId,
        @Min(1) @Max(1000) int quantity,
        String sourceLivestreamId,
        @Size(max = 3000) String livePurchaseToken) {
    public InventoryReservationLine(String productId, String variantId, int quantity, String sourceLivestreamId) {
        this(productId, variantId, quantity, sourceLivestreamId, null);
    }

    public InventoryReservationLine(String productId, String variantId, int quantity) {
        this(productId, variantId, quantity, null, null);
    }
}
