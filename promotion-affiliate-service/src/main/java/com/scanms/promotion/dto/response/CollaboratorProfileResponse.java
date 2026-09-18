package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CollaboratorProfileResponse(
        UUID collaboratorId,
        UUID userId,
        CollaboratorStatus approvalStatus,
        LocalDateTime joinedAt,
        String policyVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
