package dev.zynema.common.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The relay: reads unpublished rows and hands them to the broker.
 *
 * <p>At-least-once by design (ADR-0008). A crash between publishing and
 * marking republishes on the next tick, which is why consumers deduplicate by
 * {@code eventId} instead of the platform pretending it can promise
 * exactly-once. Rows are published in insertion order and the batch stops at
 * the first failure: the broker is usually the thing that is down, and the
 * events keep whatever order the outbox gave them.
 */
@Slf4j
public class OutboxRelay {

    private static final String PENDING = """
        SELECT id, topic, subject, type, payload, correlation_id, occurred_at
        FROM outbox
        WHERE published_at IS NULL
        ORDER BY created_at
        LIMIT ?
        """;

    private static final String MARK_PUBLISHED = "UPDATE outbox SET published_at = now() WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final OutboxSerializer serializer;
    private final EventPublisher publisher;
    private final String source;
    private final int batchSize;

    public OutboxRelay(JdbcTemplate jdbc, OutboxSerializer serializer, EventPublisher publisher,
                       String source, int batchSize) {
        this.jdbc = jdbc;
        this.serializer = serializer;
        this.publisher = publisher;
        this.source = source;
        this.batchSize = batchSize;
    }

    /**
     * The initial delay is a property on purpose: tests set it to something
     * huge so the relay only runs when a test calls it, instead of racing the
     * scheduler for the assertions.
     */
    @Scheduled(initialDelayString = "${zynema.messaging.outbox.initial-delay:5s}",
               fixedDelayString = "${zynema.messaging.outbox.poll-interval:2s}")
    public void publishPending() {
        for (OutboxMessage message : pending()) {
            try {
                publish(message);
                jdbc.update(MARK_PUBLISHED, message.id());
            } catch (RuntimeException ex) {
                // Stop the batch: publishing out of order to a broker that is
                // half-available is worse than publishing later.
                log.warn("Outbox relay stopped at {}: {}", message.id(), ex.getMessage());
                return;
            }
        }
    }

    private void publish(OutboxMessage message) {
        Object payload = serializer.read(message.type(), message.payload());
        EventMetadata metadata = new EventMetadata(message.id(), message.type(), source,
            message.occurredAt(), "1.0", message.correlationId());
        publisher.publish(message.topic(), message.subject(), metadata, payload).join();
    }

    List<OutboxMessage> pending() {
        List<OutboxMessage> messages = jdbc.query(PENDING, (rs, rowNum) -> new OutboxMessage(
            rs.getObject("id", UUID.class),
            rs.getString("topic"),
            rs.getString("subject"),
            rs.getString("type"),
            rs.getString("payload"),
            rs.getString("correlation_id"),
            rs.getTimestamp("occurred_at").toInstant()), batchSize);
        if (!messages.isEmpty()) {
            log.debug("Outbox relay picked {} unpublished message(s)", messages.size());
        }
        return messages;
    }

    record OutboxMessage(UUID id, String topic, String subject, String type,
                         String payload, String correlationId, Instant occurredAt) {
    }
}
