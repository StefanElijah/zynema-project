package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


import java.time.Instant;
import java.util.UUID;

/**
 * Events published by playback-service on {@link KafkaTopics#PLAYBACK_EVENTS}.
 *
 * <p>These are the events that <em>are</em> the session data: with event
 * sourcing (ADR-0009) the playlist of a session is its event stream, and this
 * topic is the same stream shared with the rest of the platform.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PlaybackEvent.SessionStarted.class, name = "session-started"),
    @JsonSubTypes.Type(value = PlaybackEvent.SessionPaused.class, name = "session-paused"),
    @JsonSubTypes.Type(value = PlaybackEvent.SessionResumed.class, name = "session-resumed"),
    @JsonSubTypes.Type(value = PlaybackEvent.SessionProgressed.class, name = "session-progressed"),
    @JsonSubTypes.Type(value = PlaybackEvent.SessionStopped.class, name = "session-stopped")
})
public sealed interface PlaybackEvent
    permits PlaybackEvent.SessionStarted, PlaybackEvent.SessionPaused,
            PlaybackEvent.SessionResumed, PlaybackEvent.SessionProgressed,
            PlaybackEvent.SessionStopped {

    UUID sessionId();

    UUID userId();

    UUID contentId();

    Instant occurredAt();

    /**
     * Carries the whole start state, not just the ids: with event sourcing the
     * session <em>is</em> the fold of its events (ADR-0009, ADR-0027), so the
     * first event has to be enough to build the aggregate from nothing. The
     * denormalised title and the device are the fields a resume and the
     * analytics pipeline need without calling catalog.
     */
    record SessionStarted(
        UUID sessionId,
        UUID userId,
        UUID profileId,
        UUID contentId,
        UUID episodeId,
        String contentTitle,
        String device,
        Integer positionSeconds,
        Integer durationSeconds,
        Instant occurredAt
    ) implements PlaybackEvent {
    }

    record SessionPaused(
        UUID sessionId,
        UUID userId,
        UUID contentId,
        Integer positionSeconds,
        Instant occurredAt
    ) implements PlaybackEvent {
    }

    record SessionResumed(
        UUID sessionId,
        UUID userId,
        UUID contentId,
        Integer positionSeconds,
        Instant occurredAt
    ) implements PlaybackEvent {
    }

    /** A heartbeat: the position moved. */
    record SessionProgressed(
        UUID sessionId,
        UUID userId,
        UUID contentId,
        Integer positionSeconds,
        Integer durationSeconds,
        Instant occurredAt
    ) implements PlaybackEvent {
    }

    record SessionStopped(
        UUID sessionId,
        UUID userId,
        UUID contentId,
        Integer positionSeconds,
        Integer watchedSeconds,
        Instant occurredAt
    ) implements PlaybackEvent {
    }
}
