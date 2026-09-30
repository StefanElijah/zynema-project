package dev.zynema.common.messaging;

import dev.zynema.events.EventEnvelope;
import dev.zynema.events.EventTypes;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import dev.zynema.common.web.CorrelationIdFilter;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes a domain payload with its metadata as headers.
 *
 * <p>Three things are decided here, once, instead of in every producer:
 * <ul>
 *   <li><strong>the key is the aggregate id</strong>, which is what keeps one
 *       aggregate's events ordered (Kafka orders within a partition);</li>
 *   <li><strong>the type is derived</strong> from the topic and the payload
 *       class ({@code zynema.payment.events} +
 *       {@code SubscriptionCreated} → {@code payment.subscription-created}),
 *       so routing and logging never need to deserialise the payload;</li>
 *   <li><strong>the correlation id travels</strong> in a header, taken from the
 *       request's MDC, so an asynchronous hop stays in one log trail. Tracing
 *       needs nothing from us: the Kafka instrumentation propagates it.</li>
 * </ul>
 *
 * <p>The returned future is what the outbox relay waits on before marking a row
 * published — fire-and-forget is fine for telemetry, not for an event that is
 * the point of a transaction.
 */
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String source;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, String source) {
        this.kafkaTemplate = kafkaTemplate;
        this.source = source;
    }

    public CompletableFuture<Void> publish(String topic, String subject, Object payload) {
        return publish(topic, subject,
            EventMetadata.of(source, topic, payload, MDC.get(CorrelationIdFilter.MDC_KEY)), payload);
    }

    /**
     * The relay's entry point: the metadata already exists (it is what the
     * outbox stored) and must not be regenerated — the {@code eventId} is the
     * consumers' idempotency key, and a republished event that changed it would
     * be processed twice.
     */
    public CompletableFuture<Void> publish(String topic, String subject, EventMetadata metadata, Object payload) {
        ProducerRecord<String, Object> record =
            new ProducerRecord<>(topic, null, subject, payload, metadata.toHeaders());

        return kafkaTemplate.send(record)
            .thenApply(result -> (Void) null)
            .whenComplete((sent, failure) -> {
                if (failure != null) {
                    log.warn("Could not publish {} to {}: {}", metadata.type(), topic, failure.getMessage());
                } else {
                    log.debug("Published {} ({}) to {}", metadata.type(), metadata.eventId(), topic);
                }
            });
    }

    /** {@code zynema.payment.events} + {@code SubscriptionCreated} → {@code payment.subscription-created}. */
    static String typeOf(String topic, Object payload) {
        return EventTypes.forPayload(EventTypes.domainOf(topic), payload);
    }
}
