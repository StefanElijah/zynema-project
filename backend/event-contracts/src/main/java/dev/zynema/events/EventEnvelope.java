package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A message as the application sees it, whichever side of the topic you are on.
 *
 * <p><strong>Not what travels on the wire.</strong> The wire carries the
 * payload as the value (so the Schema Registry sees the concrete event and can
 * compatibility-check it) and the metadata in headers (see {@code EventMetadata}
 * in zynema-common). Producers build one through the publisher, consumers
 * rebuild one from a record — this record is the shared shape of both.
 *
 * @param eventId       unique per event, the consumer's idempotency key
 * @param type          e.g. "payment.subscription-created" (see {@link EventTypes})
 * @param source        service that produced the event ({@code spring.application.name})
 * @param time          when the event happened, not when it was published
 * @param version       contract version, bumped on a breaking change
 * @param subject       aggregate id this event refers to (the partition key)
 * @param correlationId ties every event of one flow together (a saga, a request)
 * @param payload       type-specific payload, deserialised as its concrete record
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
    UUID eventId,
    String type,
    String source,
    Instant time,
    String version,
    String subject,
    String correlationId,
    Map<String, String> metadata,
    T payload
) {

    public static <T> EventEnvelope<T> of(String type, String source, String subject, T payload) {
        return of(type, source, subject, null, payload);
    }

    public static <T> EventEnvelope<T> of(String type, String source, String subject,
                                          String correlationId, T payload) {
        return new EventEnvelope<>(
            UUID.randomUUID(),
            type,
            source,
            Instant.now(),
            "1.0",
            subject,
            correlationId,
            Map.of(),
            payload
        );
    }
}
