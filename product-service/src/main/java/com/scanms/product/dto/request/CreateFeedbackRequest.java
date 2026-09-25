package com.scanms.product.dto.request;

import com.scanms.product.constant.FeedbackStatus;
import jakarta.validation.constraints.*;
import java.util.*;

public record CreateFeedbackRequest(
        @NotNull String productId,
        @NotNull String orderItemId,
        @NotNull String customerId,
        @NotNull @Min(1) @Max(5) Integer rating,
        String comment,
        List<String> imageRefs,
        @NotNull FeedbackStatus status
) {}

