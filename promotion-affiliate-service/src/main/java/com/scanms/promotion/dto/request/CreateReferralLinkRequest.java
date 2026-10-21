package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateReferralLinkRequest(
        String collaboratorId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String productId,
        String livestreamId,
        @Pattern(regexp = "[A-Za-z0-9_-]{6,120}") String token,
        ReferralLinkStatus status,
        LocalDateTime expiresAt,
        @Size(min = 2, max = 150) String label,
        @Pattern(regexp = "TIKTOK|FACEBOOK|YOUTUBE|INSTAGRAM|WEBSITE|OTHER") String channel
) {}
