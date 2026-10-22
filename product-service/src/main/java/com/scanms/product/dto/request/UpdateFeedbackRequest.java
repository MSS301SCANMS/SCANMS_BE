package com.scanms.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record UpdateFeedbackRequest(@Min(1) @Max(5) Integer rating,
        @jakarta.validation.constraints.Size(max=1000) String comment,
        @jakarta.validation.constraints.Size(max=5) List<@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2048) String> imageRefs) {}
