package com.scanms.product.dto.request;

import com.scanms.product.constant.FeedbackStatus;
import jakarta.validation.constraints.*;
import java.util.*;

public record CreateFeedbackRequest(
        @NotBlank String productId,
        @NotBlank String orderItemId,
        String customerId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max=1000) String comment,
        @Size(max=5) List<@NotBlank @Size(max=2048) String> imageRefs,
        FeedbackStatus status
) {}

