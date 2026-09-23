package dev.zynema.user.dto;

import java.time.Instant;
import java.util.UUID;

public record WatchlistItemDto(
    UUID id,
    UUID contentId,
    Instant addedAt
) {
}
