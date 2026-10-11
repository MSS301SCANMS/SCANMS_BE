package com.scanms.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record RegisterRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String firstName,
        @NotBlank String lastName
) {}
