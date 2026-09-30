package dev.zynema.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Progress update for a movie ({@code episodeId} null) or an episode.
 * The operation is idempotent: replaying the same payload overwrites the
 * existing row instead of creating a new one.
 */
public record WatchProgressRequest(

    @NotNull(message = "contentId is required")
    UUID contentId,

    UUID episodeId,

    @NotNull(message = "positionSeconds is required")
    @Min(value = 0, message = "positionSeconds must not be negative")
    Integer positionSeconds,

    @Min(value = 0, message = "durationSeconds must not be negative")
    Integer durationSeconds,

    Boolean completed
) {
}
