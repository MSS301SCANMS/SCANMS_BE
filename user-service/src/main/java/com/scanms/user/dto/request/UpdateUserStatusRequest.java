package com.scanms.user.dto.request;

import com.scanms.user.constant.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull
        UserStatus status
) {}
