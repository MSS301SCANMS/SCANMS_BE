package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ReferralLinkResponse(
        UUID referralLinkId,
        UUID collaboratorId,
        UUID productId,
        String token,
        ReferralLinkStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {}
