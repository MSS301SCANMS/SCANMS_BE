package com.scanms.user.dto.request;

import jakarta.validation.constraints.*;

public record UpdateUserProfileRequest(
        @Email String email,
        @Size(min = 2, max = 150) String fullName
) {}
