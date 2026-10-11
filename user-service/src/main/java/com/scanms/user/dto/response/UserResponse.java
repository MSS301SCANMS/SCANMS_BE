package com.scanms.user.dto.response;

import com.scanms.user.constant.Gender;
import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        String userId,
        String identitySubject,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        Gender gender,
        String address,
        String avatarUrl,
        String bio,
        UserStatus status,
        Set<RoleName> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
