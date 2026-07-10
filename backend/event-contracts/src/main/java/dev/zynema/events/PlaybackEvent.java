package dev.zynema.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Events published by playback-service.
 * Naming: zynema.playback.session.&lt;verb&gt;
 */
public sealed interface PlaybackEvent
    permits PlaybackEvent.SessionStarted, PlaybackEvent.SessionPaused, PlaybackEvent.SessionResumed, PlaybackEvent.SessionStopped {

    UUID sessionId();
    UUID userId();
    UUID contentId();
    Instant occurredAt();

    record SessionStarted(UUID sessionId, UUID userId, UUID contentId, Integer positionSeconds, Instant occurredAt) implements PlaybackEvent {}
    record SessionPaused(UUID sessionId, UUID userId, UUID contentId, Integer positionSeconds, Instant occurredAt) implements PlaybackEvent {}
    record SessionResumed(UUID sessionId, UUID userId, UUID contentId, Integer positionSeconds, Instant occurredAt) implements PlaybackEvent {}
    record SessionStopped(UUID sessionId, UUID userId, UUID contentId, Integer positionSeconds, Integer watchedSeconds, Instant occurredAt) implements PlaybackEvent {}
}
