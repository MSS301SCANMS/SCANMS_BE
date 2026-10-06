package com.scanms.product.dto.response;

import com.scanms.product.constant.FeedbackStatus;
import java.time.Instant;
import java.util.*;

public record FeedbackResponse(
        String feedbackId,
        String productId,
        String orderItemId,
        String customerId,
        Integer rating,
        String comment,
        List<String> imageRefs,
        FeedbackStatus status,
        Instant createdAt,
        Instant updatedAt
) {}

