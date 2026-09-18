package com.scanms.user.dto.response;

import com.scanms.user.constant.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(UUID userId, String identitySubject, String email, String fullName,
                           UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {}
