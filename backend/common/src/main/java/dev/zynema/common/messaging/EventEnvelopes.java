package dev.zynema.common.messaging;

import dev.zynema.events.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Rebuilds the application view of a received message: the payload is already
 * typed (the listener declares its domain), the headers are the metadata and
 * the key is the aggregate id.
 */
public final class EventEnvelopes {

    private EventEnvelopes() {
    }

    public static <T> EventEnvelope<T> of(ConsumerRecord<String, T> record) {
        return EventMetadata.from(record.headers()).envelop(record.key(), record.value());
    }
}
