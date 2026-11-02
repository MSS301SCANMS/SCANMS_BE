package com.scanms.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreChatMessageRequest(
        @NotBlank @Size(max = 10000) String messageText,
        @Size(max = 36) String messageId) {
}
