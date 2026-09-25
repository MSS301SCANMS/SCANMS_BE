package com.scanms.promotion.dto.response;

import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record CollaboratorProfileResponse(
        String collaboratorId,
        String userId,
        CollaboratorStatus approvalStatus,
        LocalDateTime joinedAt,
        String policyVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
