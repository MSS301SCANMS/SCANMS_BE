package com.scanms.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record RegisterRequest(
        @NotBlank
        @Size(max = 255)
        String username,
        @Email
        @NotBlank
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(min = 6)
        String password,
        @NotBlank
        @Size(max = 255)
        String firstName,
        @NotBlank
        @Size(max = 255)
        String lastName
) {
    public RegisterRequest {
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
    }
}
