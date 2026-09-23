package dev.zynema.user.dto;

import java.time.Instant;
import java.util.UUID;

public record WatchHistoryDto(
    UUID id,
    UUID contentId,
    UUID episodeId,
    Integer positionSeconds,
    Integer durationSeconds,
    boolean completed,
    Instant lastWatchedAt
) {
}
