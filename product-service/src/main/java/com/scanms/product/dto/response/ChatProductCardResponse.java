package com.scanms.product.dto.response;

public record ChatProductCardResponse(String type, String productId, String productTitle,
                                      Long productPrice, String productImage, String message) {
}
