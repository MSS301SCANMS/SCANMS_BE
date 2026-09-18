package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CreateReferralLinkRequest(
        @NotNull UUID collaboratorId,
        @NotNull UUID productId,
        @NotBlank String token,
        ReferralLinkStatus status,
        LocalDateTime expiresAt
) {}
