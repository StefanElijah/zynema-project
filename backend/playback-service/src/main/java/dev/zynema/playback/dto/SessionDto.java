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
    /**
     * Relative path of the master playlist. Relative on purpose: the client
     * knows its own API base, and the token it must send is the client's, not
     * the server's.
     */
    String streamPath,
    Instant startedAt,
    Instant lastHeartbeatAt,
    Instant endedAt
) {
}
