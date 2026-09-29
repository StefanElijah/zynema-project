package dev.zynema.common.messaging;

import dev.zynema.common.web.CorrelationIdFilter;
import dev.zynema.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * The producer's half of the outbox pattern (ADR-0008, ADR-0026).
 *
 * <p>An event is appended <strong>inside the business transaction</strong>:
 * the row and the state change commit together or roll back together, so there
 * is no window in which one exists without the other. The relay publishes it
 * afterwards, at least once, and consumers deduplicate by {@code eventId}
 * (see {@link ProcessedEventStore}).
 *
 * <p>The payload is stored as JSON because that is what will be published; the
 * relay turns it back into the concrete record through {@link EventTypes}, the
 * same registry the producers derive the type from.
 *
 * <p>Each producing service creates the table in its own migration:
 * <pre>
 * create table outbox (
 *     id           uuid         primary key,
 *     topic        varchar(120) not null,
 *     subject      varchar(255) not null,
 *     type         varchar(120) not null,
 *     payload      text         not null,
 *     occurred_at  timestamptz  not null,
 *     created_at   timestamptz  not null default now(),
 *     published_at timestamptz
 * );
 * create index idx_outbox_unpublished on outbox (created_at) where published_at is null;
 * </pre>
 */
@RequiredArgsConstructor
public class OutboxRecorder {

    private static final String INSERT = """
        INSERT INTO outbox (id, topic, subject, type, payload, correlation_id, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbc;
    private final OutboxSerializer serializer;

    /**
     * @param payload the event or command record; its JSON is what gets stored
     *                and later published
     */
    public UUID append(String topic, String subject, Object payload) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String type = EventTypes.forPayload(EventTypes.domainOf(topic), payload);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        jdbc.update(INSERT, eventId, topic, subject, type,
            serializer.write(payload), correlationId, java.sql.Timestamp.from(occurredAt));
        return eventId;
    }
}
