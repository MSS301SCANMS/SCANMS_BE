package com.scanms.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateLivestreamReactionCountRequest(@Min(0) @Max(9007199254740991L) long likeCount) {
}
