package dev.zynema.playback.events;

import dev.zynema.events.PlaybackEvent;
import dev.zynema.playback.domain.SessionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The current state of one session, rebuilt by folding its events
 * (ADR-0009, ADR-0027).
 *
 * <p>The record has no persistence annotations on purpose: it is not a row, it
 * is <em>the result of applying the log</em>. Snapshotting serialises it to
 * JSON, the projection writes it to {@code playback_sessions} in the same
 * transaction, and loading it replays a snapshot plus the events after it.
 *
 * <p>The only mutable-looking thing went away with the entity: every command
 * produces one event, and {@link #apply} evolves the state. The fold is the
 * single definition of what the session means, so the projection and a replay
 * can never disagree about it.
 */
public record SessionState(
    UUID sessionId,
    UUID userId,
    UUID profileId,
    UUID contentId,
    UUID episodeId,
    SessionStatus status,
    String contentTitle,
    int positionSeconds,
    Integer durationSeconds,
    int watchedSeconds,
    String device,
    Instant startedAt,
    Instant lastHeartbeatAt,
    Instant endedAt
) {

    /**
     * The fold: one event in, the next state out.
     *
     * <p>It rejects streams that do not make sense instead of tolerating them:
     * a second {@code SessionStarted}, an event from another session or
     * anything after {@code SessionStopped} is a corrupt stream and must fail
     * loudly while the transaction can still roll back.
     */
    public static SessionState apply(SessionState state, PlaybackEvent event) {
        if (event instanceof PlaybackEvent.SessionStarted started) {
            if (state != null) {
                throw new IllegalStateException(
                    "Session " + started.sessionId() + " already has an event stream");
            }
            return new SessionState(started.sessionId(), started.userId(), started.profileId(),
                started.contentId(), started.episodeId(), SessionStatus.STARTED, started.contentTitle(),
                position(started.positionSeconds()), started.durationSeconds(), 0, started.device(),
                started.occurredAt(), started.occurredAt(), null);
        }

        requireOpenStream(state, event);
        return switch (event) {
            case PlaybackEvent.SessionProgressed progressed -> progressed(state, progressed);
            case PlaybackEvent.SessionPaused paused -> moved(state, SessionStatus.PAUSED, paused.positionSeconds(), paused.occurredAt());
            case PlaybackEvent.SessionResumed resumed -> moved(state, SessionStatus.STARTED, resumed.positionSeconds(), resumed.occurredAt());
            case PlaybackEvent.SessionStopped stopped -> stopped(state, stopped);
            case PlaybackEvent.SessionStarted ignored -> throw new IllegalStateException(
                "Unreachable: SessionStarted is handled above");
        };
    }

    public boolean isOpen() {
        return status != SessionStatus.ENDED;
    }

    /**
     * How much of the title has been watched if the player stops at
     * {@code positionSeconds}: the sum of the forward position deltas.
     *
     * <p>It is an approximation on purpose — a seek forward counts, a seek
     * backward does not subtract — because the alternative (wall time between
     * heartbeats) counts a paused player. The number is for analytics, not
     * billing.
     */
    public int watchedAt(int positionSeconds) {
        return watchedSeconds + Math.max(0, positionSeconds - this.positionSeconds);
    }

    // ───────────────────────────── helpers ────────────────────────────

    private static SessionState progressed(SessionState state, PlaybackEvent.SessionProgressed event) {
        int position = position(event.positionSeconds());
        return new SessionState(state.sessionId(), state.userId(), state.profileId(), state.contentId(),
            state.episodeId(), state.status(), state.contentTitle(), position,
            event.durationSeconds() != null ? event.durationSeconds() : state.durationSeconds(),
            state.watchedSeconds() + Math.max(0, position - state.positionSeconds()),
            state.device(), state.startedAt(), event.occurredAt(), state.endedAt());
    }

    private static SessionState moved(SessionState state, SessionStatus status, Integer rawPosition, Instant when) {
        return new SessionState(state.sessionId(), state.userId(), state.profileId(), state.contentId(),
            state.episodeId(), status, state.contentTitle(), position(rawPosition), state.durationSeconds(),
            state.watchedSeconds(), state.device(), state.startedAt(), when, null);
    }

    private static SessionState stopped(SessionState state, PlaybackEvent.SessionStopped event) {
        int position = position(event.positionSeconds());
        int watched = event.watchedSeconds() != null ? event.watchedSeconds()
            : state.watchedSeconds() + Math.max(0, position - state.positionSeconds());
        return new SessionState(state.sessionId(), state.userId(), state.profileId(), state.contentId(),
            state.episodeId(), SessionStatus.ENDED, state.contentTitle(), position, state.durationSeconds(),
            watched, state.device(), state.startedAt(), event.occurredAt(), event.occurredAt());
    }

    private static void requireOpenStream(SessionState state, PlaybackEvent event) {
        if (state == null) {
            throw new IllegalStateException("The first event of a session must be SessionStarted, got "
                + event.getClass().getSimpleName());
        }
        if (!state.sessionId().equals(event.sessionId())) {
            throw new IllegalStateException("Event for session " + event.sessionId()
                + " cannot be applied to session " + state.sessionId());
        }
        if (!state.isOpen()) {
            throw new IllegalStateException("Session " + state.sessionId()
                + " is closed: nothing can follow SessionStopped");
        }
    }

    private static int position(Integer positionSeconds) {
        return positionSeconds == null ? 0 : Math.max(0, positionSeconds);
    }
}
