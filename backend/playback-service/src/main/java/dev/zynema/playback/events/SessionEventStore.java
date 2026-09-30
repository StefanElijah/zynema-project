package dev.zynema.playback.events;

import dev.zynema.common.exception.ConflictException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.OutboxSerializer;
import dev.zynema.events.EventTypes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.PlaybackEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * The session aggregate's event log: append-only, per-session ordered, with
 * snapshots to keep the fold short (ADR-0009, ADR-0027).
 *
 * <p>{@link #append} does three things in the caller's transaction, and that is
 * the entire consistency story:
 * <ol>
 *   <li>inserts the event row with the next sequence,</li>
 *   <li>records the same fact in the outbox <strong>with the same event
 *       id</strong> (ADR-0026), so the log entry and the Kafka message that
 *       eventually leaves the service are one fact with one identity,</li>
 *   <li>writes a snapshot every {@code N} events.</li>
 * </ol>
 *
 * <p>The sequence check is optimistic: two writers that loaded the same stream
 * collide on {@code (session_id, sequence)} and the loser gets a 409 instead of
 * an interleaved stream. Retrying inside the aborted PostgreSQL transaction is
 * impossible, so the retry belongs to the caller.
 */
@Component
public class SessionEventStore {

    private static final String INSERT_EVENT = """
        INSERT INTO session_events (event_id, session_id, sequence, type, payload, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_SNAPSHOT =
        "SELECT sequence, state FROM session_snapshots WHERE session_id = ?";

    private static final String SELECT_EVENTS = """
        SELECT type, payload FROM session_events
        WHERE session_id = ? AND sequence > ?
        ORDER BY sequence
        """;

    private static final String UPSERT_SNAPSHOT = """
        INSERT INTO session_snapshots (session_id, sequence, state)
        VALUES (?, ?, ?)
        ON CONFLICT (session_id) DO UPDATE
            SET sequence = EXCLUDED.sequence, state = EXCLUDED.state, created_at = now()
        """;

    private final JdbcTemplate jdbc;
    private final OutboxSerializer serializer;
    private final OutboxRecorder outbox;
    private final int snapshotEvery;

    public SessionEventStore(JdbcTemplate jdbc, OutboxSerializer serializer, OutboxRecorder outbox,
                             @Value("${zynema.playback.events.snapshot-every:20}") int snapshotEvery) {
        this.jdbc = jdbc;
        this.serializer = serializer;
        this.outbox = outbox;
        this.snapshotEvery = snapshotEvery;
    }

    /**
     * Rebuilds the aggregate: the latest snapshot, then every event after it.
     * A session without a stream does not exist as far as the write side is
     * concerned (the read side's projection may still hold rows seeded by
     * tooling, which is why this is a 404 and not an empty state).
     */
    public SessionStream load(UUID sessionId) {
        SessionSnapshot snapshot = snapshotOf(sessionId);
        SessionState state = snapshot == null ? null : serializer.read(snapshot.state(), SessionState.class);
        long sequence = snapshot == null ? 0 : snapshot.sequence();

        List<PlaybackEvent> tail = jdbc.query(SELECT_EVENTS, (rs, rowNum) ->
            (PlaybackEvent) serializer.read(rs.getString("type"), rs.getString("payload")),
            sessionId, sequence);

        for (PlaybackEvent event : tail) {
            state = SessionState.apply(state, event);
            sequence++;
        }
        if (state == null) {
            throw new ResourceNotFoundException("Playback session", sessionId);
        }
        return new SessionStream(state, sequence);
    }

    /**
     * Appends one event and returns the stream it produced. Callers must have
     * loaded the stream in the same transaction, or the sequence check is what
     * catches them.
     */
    public SessionStream append(SessionStream stream, PlaybackEvent event) {
        long sequence = stream.lastSequence() + 1;
        SessionState state = SessionState.apply(stream.state(), event);
        UUID eventId = UUID.randomUUID();
        String type = EventTypes.forPayload(EventTypes.domainOf(KafkaTopics.PLAYBACK_EVENTS), event);

        try {
            jdbc.update(INSERT_EVENT, eventId, event.sessionId(), sequence, type,
                serializer.write(event), Timestamp.from(event.occurredAt()));
        } catch (DuplicateKeyException ex) {
            throw new ConflictException(
                "The session event stream advanced concurrently: reload and retry the request");
        }

        outbox.append(KafkaTopics.PLAYBACK_EVENTS, event.sessionId().toString(),
            eventId, event.occurredAt(), event);

        if (sequence % snapshotEvery == 0) {
            jdbc.update(UPSERT_SNAPSHOT, event.sessionId(), sequence, serializer.write(state));
        }
        return new SessionStream(state, sequence);
    }

    private SessionSnapshot snapshotOf(UUID sessionId) {
        return jdbc.query(SELECT_SNAPSHOT,
            rs -> rs.next() ? new SessionSnapshot(rs.getLong("sequence"), rs.getString("state")) : null,
            sessionId);
    }

    record SessionSnapshot(long sequence, String state) {
    }

    /**
     * An aggregate and the position it has reached in the log. {@code empty()}
     * is the only stream a session can start from: the first append must be
     * {@code SessionStarted}, which the fold enforces.
     */
    public record SessionStream(SessionState state, long lastSequence) {

        public static SessionStream empty() {
            return new SessionStream(null, 0);
        }
    }
}
