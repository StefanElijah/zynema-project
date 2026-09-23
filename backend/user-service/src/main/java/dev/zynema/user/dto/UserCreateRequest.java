package dev.zynema.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    @Size(max = 255, message = "email must be at most 255 characters")
    String email,

    @Size(max = 120, message = "displayName must be at most 120 characters")
    String displayName,

    @Size(max = 500, message = "avatarUrl must be at most 500 characters")
    String avatarUrl,

    @Size(max = 10, message = "preferredLanguage must be at most 10 characters")
    String preferredLanguage
) {
}
