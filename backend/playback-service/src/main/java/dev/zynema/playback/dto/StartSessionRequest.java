package dev.zynema.playback.dto;

import dev.zynema.playback.domain.SessionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request to open (or resume) a session. */
public record StartSessionRequest(

    @NotNull(message = "profileId is required")
    UUID profileId,

    @NotNull(message = "contentId is required")
    UUID contentId,

    UUID episodeId,

    @Size(max = 60, message = "device must be at most 60 characters")
    String device
) {
}
