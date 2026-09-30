package dev.zynema.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ProfileDto(
    UUID id,
    String name,
    String avatarKey,
    boolean kids,
    String language,
    Instant createdAt
) {
}
