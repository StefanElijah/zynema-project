package dev.zynema.common.messaging;

import dev.zynema.events.EventEnvelope;
import dev.zynema.events.PaymentEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventPublisherTests {

    private static final String TOPIC = "zynema.payment.events";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("the payload is the value, the key is the aggregate id and the metadata is in headers")
    void publishesPayloadWithHeaderMetadata() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate, "payment-service");
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        MDC.put("correlationId", "corr-42");

        PaymentEvent.SubscriptionCreated payload = new PaymentEvent.SubscriptionCreated(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "standard",
            new BigDecimal("9.99"), "USD", Instant.now());

        publisher.publish(TOPIC, payload.subscriptionId().toString(), payload).join();

        var record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());

        assertThat(record.getValue().topic()).isEqualTo(TOPIC);
        assertThat(record.getValue().key()).isEqualTo(payload.subscriptionId().toString());
        // The value is the event itself: the registry sees the concrete schema.
        assertThat(record.getValue().value()).isSameAs(payload);

        EventMetadata metadata = EventMetadata.from(record.getValue().headers());
        assertThat(metadata.type()).isEqualTo("payment.subscription-created");
        assertThat(metadata.source()).isEqualTo("payment-service");
        assertThat(metadata.correlationId()).isEqualTo("corr-42");
        assertThat(metadata.eventId()).isNotNull();
        assertThat(metadata.time()).isNotNull();
        assertThat(metadata.version()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("the metadata survives a header round trip")
    void metadataRoundTripsThroughHeaders() {
        PaymentEvent.PaymentSucceeded payload = new PaymentEvent.PaymentSucceeded(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4.99"), "EUR", "card", Instant.now());

        EventMetadata sent = EventMetadata.of("payment-service", TOPIC, payload, "corr-7");
        EventMetadata received = EventMetadata.from(sent.toHeaders());

        assertThat(received).isEqualTo(sent);

        EventEnvelope<PaymentEvent> envelope = received.envelop("subject-1", payload);
        assertThat(envelope.type()).isEqualTo("payment.payment-succeeded");
        assertThat(envelope.subject()).isEqualTo("subject-1");
        assertThat(envelope.correlationId()).isEqualTo("corr-7");
        assertThat(envelope.payload()).isSameAs(payload);
    }

    @Test
    @DisplayName("types are derived for commands too")
    void derivesTypes() {
        assertThat(EventPublisher.typeOf("zynema.payment.events", new PaymentEvent.SubscriptionCancelled(
            UUID.randomUUID(), UUID.randomUUID(), "no reason", Instant.now())))
            .isEqualTo("payment.subscription-cancelled");
        assertThat(EventPublisher.typeOf("zynema.user.commands",
            new dev.zynema.events.UserCommand.GrantRole(UUID.randomUUID(), UUID.randomUUID(), "subscriber")))
            .isEqualTo("user.grant-role");
    }
}
