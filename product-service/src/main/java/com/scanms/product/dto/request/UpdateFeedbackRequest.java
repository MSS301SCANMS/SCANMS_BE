package com.scanms.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record UpdateFeedbackRequest(@Min(1) @Max(5) Integer rating,
        String comment, List<String> imageRefs) {}
