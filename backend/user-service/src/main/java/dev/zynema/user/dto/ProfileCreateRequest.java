package dev.zynema.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileCreateRequest(

    @NotBlank(message = "name is required")
    @Size(max = 80, message = "name must be at most 80 characters")
    String name,

    @Size(max = 40, message = "avatarKey must be at most 40 characters")
    String avatarKey,

    Boolean kids,

    @Size(max = 10, message = "language must be at most 10 characters")
    String language
) {
}
