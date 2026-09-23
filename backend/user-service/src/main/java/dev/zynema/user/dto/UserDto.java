package dev.zynema.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    String preferredLanguage,
    Instant createdAt,
    List<ProfileDto> profiles
) {
}
