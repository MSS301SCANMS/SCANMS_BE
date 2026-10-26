package com.scanms.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFeedbackRequest(@Min(1) @Max(5) Integer rating,
        @Size(max=1000) String comment,
        @Size(max=5) List<@NotBlank @Size(max=2048) String> imageRefs) {}
