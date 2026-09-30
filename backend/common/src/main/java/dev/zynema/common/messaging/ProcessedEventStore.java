package dev.zynema.common.messaging;

import dev.zynema.events.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Idempotency for consumers, in one statement.
 *
 * <p>Kafka delivers at least once (ADR-0008), so every consumer must be able to
 * answer "have I already handled this event?". The answer is a unique row in
 * {@code processed_events}, inserted with the business work in the same
 * transaction: two concurrent deliveries cannot both win, and a rolled-back
 * handler leaves no claim behind.
 *
 * <p>PostgreSQL's {@code ON CONFLICT DO NOTHING} returns 0 when the row was
 * already there, which is exactly the "seen it" signal. No read-then-write
 * race, no Redis key that can expire before the retry window closes.
 *
 * <p>Each consumer service creates the table (see the migration conventions in
 * ADR-0026):
 * <pre>
 * create table processed_events (
 *     event_id     uuid        not null,
 *     handler      varchar(120) not null,
 *     processed_at timestamptz not null default now(),
 *     primary key (event_id, handler)
 * );
 * </pre>
 */
@RequiredArgsConstructor
public class ProcessedEventStore {

    private static final String INSERT = """
        INSERT INTO processed_events (event_id, handler)
        VALUES (?, ?)
        ON CONFLICT (event_id, handler) DO NOTHING
        """;

    private final JdbcTemplate jdbc;

    public boolean isNew(EventEnvelope<?> envelope, String handler) {
        return claim(envelope.eventId(), handler);
    }

    public boolean claim(UUID eventId, String handler) {
        return jdbc.update(INSERT, eventId, handler) == 1;
    }
}
