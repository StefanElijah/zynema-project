package dev.zynema.common.messaging;

import dev.zynema.events.EventEnvelope;
import dev.zynema.events.EventTypes;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * The metadata of a message, as Kafka headers.
 *
 * <p>Why headers and not an envelope object on the wire: with the payload as
 * the value, the Schema Registry sees the concrete event type and
 * compatibility-checks <em>that</em> schema. Wrapping everything in one generic
 * envelope collapses every event of a topic into a single, permissive schema —
 * the registry would happily accept a payload it should reject.
 *
 * <p>Tracing is deliberately absent: the Kafka observation instrumentation
 * already propagates {@code traceparent} in headers, and duplicating it here
 * would create two sources of truth for the same fact.
 */
@Slf4j
public record EventMetadata(
    UUID eventId,
    String type,
    String source,
    Instant time,
    String version,
    String correlationId
) {

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_TYPE = "eventType";
    public static final String HEADER_SOURCE = "eventSource";
    public static final String HEADER_TIME = "eventTime";
    public static final String HEADER_VERSION = "eventVersion";
    public static final String HEADER_CORRELATION_ID = "correlationId";

    public static EventMetadata of(String source, String topic, Object payload, String correlationId) {
        return new EventMetadata(
            UUID.randomUUID(),
            EventTypes.forPayload(EventTypes.domainOf(topic), payload),
            source,
            Instant.now(),
            "1.0",
            correlationId);
    }

    public Headers toHeaders() {
        Headers headers = new RecordHeaders();
        put(headers, HEADER_EVENT_ID, eventId.toString());
        put(headers, HEADER_TYPE, type);
        put(headers, HEADER_SOURCE, source);
        put(headers, HEADER_TIME, time.toString());
        put(headers, HEADER_VERSION, version);
        put(headers, HEADER_CORRELATION_ID, correlationId);
        return headers;
    }

    public static EventMetadata from(Headers headers) {
        return new EventMetadata(
            uuid(read(headers, HEADER_EVENT_ID)),
            read(headers, HEADER_TYPE),
            read(headers, HEADER_SOURCE),
            instant(read(headers, HEADER_TIME)),
            read(headers, HEADER_VERSION),
            read(headers, HEADER_CORRELATION_ID));
    }

    /** The in-application view of a received message. */
    public <T> EventEnvelope<T> envelop(String subject, T payload) {
        return new EventEnvelope<>(eventId, type, source, time, version, subject, correlationId,
            java.util.Map.of(), payload);
    }

    private static void put(Headers headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(Headers headers, String key) {
        var header = headers.lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
