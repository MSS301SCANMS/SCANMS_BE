package com.scanms.promotion.dto.request;

import com.scanms.promotion.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ApplyCollaboratorRequest(
        @NotNull String userId,
        CollaboratorStatus approvalStatus,
        LocalDateTime joinedAt,
        String policyVersion
) {}
