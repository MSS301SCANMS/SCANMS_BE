package com.scanms.user.dto.request;

import com.scanms.user.constant.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateUserProfileRequest(
        @Size(min = 2, max = 150) String fullName,
        @Pattern(regexp = "^(\\+?84|0)\\d{9,10}$", message = "Số điện thoại không hợp lệ")
        String phoneNumber,
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 500) String address,
        @Size(max = 500) String avatarUrl,
        @Size(max = 500) String bio
) {}
