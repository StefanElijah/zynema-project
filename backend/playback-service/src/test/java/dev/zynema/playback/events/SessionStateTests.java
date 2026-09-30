package dev.zynema.playback.events;

import dev.zynema.events.PlaybackEvent;
import dev.zynema.playback.domain.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fold, without Spring or a database: the event list is the only input and
 * the state is the only output (ADR-0009, ADR-0027).
 */
class SessionStateTests {

    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private static final UUID CONTENT = UUID.randomUUID();
    private static final UUID EPISODE = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-09-29T10:00:00Z");

    private static final PlaybackEvent.SessionStarted STARTED = new PlaybackEvent.SessionStarted(
        SESSION, USER, PROFILE, CONTENT, EPISODE, "Arcane", "tv", 0, 2400, T0);

    @Test
    @DisplayName("the whole lifecycle folds into one state")
    void theLifecycleFolds() {
        SessionState state = SessionState.apply(null, STARTED);
        assertThat(state.status()).isEqualTo(SessionStatus.STARTED);
        assertThat(state.contentTitle()).isEqualTo("Arcane");
        assertThat(state.durationSeconds()).isEqualTo(2400);
        assertThat(state.startedAt()).isEqualTo(T0);

        state = SessionState.apply(state, progressed(600, 900));
        state = SessionState.apply(state, new PlaybackEvent.SessionPaused(
            SESSION, USER, CONTENT, 600, T0.plusSeconds(910)));
        assertThat(state.status()).isEqualTo(SessionStatus.PAUSED);

        state = SessionState.apply(state, new PlaybackEvent.SessionResumed(
            SESSION, USER, CONTENT, 600, T0.plusSeconds(1000)));
        assertThat(state.status()).isEqualTo(SessionStatus.STARTED);

        state = SessionState.apply(state, new PlaybackEvent.SessionStopped(
            SESSION, USER, CONTENT, 1200, state.watchedAt(1200), T0.plusSeconds(1800)));

        assertThat(state.status()).isEqualTo(SessionStatus.ENDED);
        assertThat(state.positionSeconds()).isEqualTo(1200);
        assertThat(state.watchedSeconds()).isEqualTo(1200);
        assertThat(state.endedAt()).isEqualTo(T0.plusSeconds(1800));
        assertThat(state.isOpen()).isFalse();
    }

    @Test
    @DisplayName("a seek forward counts as watched; a seek backward does not subtract")
    void watchedSecondsApproximatePositionDeltas() {
        SessionState state = SessionState.apply(null, STARTED);
        state = SessionState.apply(state, progressed(900, 600));
        assertThat(state.watchedSeconds()).isEqualTo(900);

        state = SessionState.apply(state, progressed(300, 700));
        assertThat(state.watchedSeconds()).isEqualTo(900);
        assertThat(state.positionSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("a stream cannot open with anything but SessionStarted")
    void theFirstEventMustBeTheStart() {
        assertThatThrownBy(() -> SessionState.apply(null, progressed(10, 600)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be SessionStarted");
    }

    @Test
    @DisplayName("a closed stream accepts nothing: SessionStopped is final")
    void nothingFollowsTheStop() {
        SessionState open = SessionState.apply(null, STARTED);
        SessionState closed = SessionState.apply(open, new PlaybackEvent.SessionStopped(
            SESSION, USER, CONTENT, 100, 100, T0.plusSeconds(100)));

        assertThatThrownBy(() -> SessionState.apply(closed, progressed(200, 200)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("an event from another session is a corrupted stream, not a merge")
    void eventsOfOtherSessionsAreRejected() {
        SessionState open = SessionState.apply(null, STARTED);
        PlaybackEvent.SessionProgressed foreign = new PlaybackEvent.SessionProgressed(
            UUID.randomUUID(), USER, CONTENT, 10, 2400, T0.plusSeconds(10));

        assertThatThrownBy(() -> SessionState.apply(open, foreign))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot be applied");
    }

    private PlaybackEvent.SessionProgressed progressed(int position, int secondsAfterStart) {
        return new PlaybackEvent.SessionProgressed(SESSION, USER, CONTENT, position, 2400,
            T0.plusSeconds(secondsAfterStart));
    }
}
