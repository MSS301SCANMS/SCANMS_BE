package com.scanms.user.dto.response;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(String userId, String identitySubject, String email, String fullName,
                           UserStatus status, Set<RoleName> roles,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {}
