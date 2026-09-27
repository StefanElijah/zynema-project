package dev.zynema.playback.dto;

import dev.zynema.playback.domain.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(
    UUID id,
    UUID profileId,
    UUID contentId,
    UUID episodeId,
    String contentTitle,
    SessionStatus status,
    Integer positionSeconds,
    Integer durationSeconds,
    String device,
    Instant startedAt,
    Instant lastHeartbeatAt,
    Instant endedAt
) {
}
