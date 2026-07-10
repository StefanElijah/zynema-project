package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base envelope for all events published to Kafka.
 * Topics are namespaced: zynema.&lt;domain&gt;.&lt;aggregate&gt;.&lt;event-name&gt;
 *
 * @param eventId   unique per event, used for idempotency
 * @param type      e.g. "payment.subscription.created"
 * @param source    service that produced the event
 * @param time      when the event happened (not when it was published)
 * @param version   schema version for evolution
 * @param subject   aggregate id this event refers to
 * @param payload   type-specific payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
    UUID eventId,
    String type,
    String source,
    Instant time,
    String version,
    String subject,
    Map<String, String> metadata,
    T payload
) {
    public static <T> EventEnvelope<T> of(String type, String source, String subject, T payload) {
        return new EventEnvelope<>(
            UUID.randomUUID(),
            type,
            source,
            Instant.now(),
            "1.0",
            subject,
            Map.of(),
            payload
        );
    }
}
