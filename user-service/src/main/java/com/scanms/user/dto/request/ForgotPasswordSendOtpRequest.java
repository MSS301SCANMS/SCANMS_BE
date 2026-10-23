package com.scanms.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordSendOtpRequest(
        @NotBlank
        @Email
        String email
) {}
