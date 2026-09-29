package dev.zynema.payment;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.PaymentEvent;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The messaging toolchain end to end, with real infrastructure: a broker, a
 * Schema Registry, the Confluent JSON Schema serialisers and the payload as the
 * message value.
 *
 * <p>What this proves that a unit test cannot: the concrete schema of each
 * event is derived, registered and compatibility-checked under its own subject;
 * the envelope metadata arrives in headers; the aggregate id is the key; and
 * the payload comes back as the very record that was sent, resolved by the
 * domain interface the listener declares.
 */
class KafkaMessagingTests extends AbstractPaymentIntegrationTest {

    @Autowired
    private EventPublisher publisher;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Test
    @DisplayName("every payment event survives the trip: broker, registry, typed payload back")
    void eventsRoundTripThroughKafkaAndTheRegistry() {
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaymentEvent.SubscriptionCreated created = new PaymentEvent.SubscriptionCreated(
            subscriptionId, userId, UUID.randomUUID(), "standard",
            new BigDecimal("9.99"), "USD", Instant.now());
        PaymentEvent.PaymentSucceeded succeeded = new PaymentEvent.PaymentSucceeded(
            subscriptionId, userId, new BigDecimal("9.99"), "USD", "card", Instant.now());
        PaymentEvent.SubscriptionCancelled cancelled = new PaymentEvent.SubscriptionCancelled(
            subscriptionId, userId, "user request", Instant.now());

        List<PaymentEvent> sent = List.of(created, succeeded, cancelled);
        sent.forEach(event -> publisher
            .publish(KafkaTopics.PAYMENT_EVENTS, subscriptionId.toString(), event)
            .join());

        List<ConsumerRecord<String, PaymentEvent>> records = drain(subscriptionId.toString(), sent.size());

        assertThat(records).hasSize(sent.size());
        assertThat(records).extracting(ConsumerRecord::value)
            .containsExactlyInAnyOrderElementsOf(sent);

        // The metadata came in headers, and the key is the aggregate id, which
        // is what keeps a subscription's events ordered.
        records.forEach(record -> {
            EventEnvelope<PaymentEvent> envelope = EventEnvelopes.of(record);
            assertThat(envelope.subject()).isEqualTo(subscriptionId.toString());
            assertThat(envelope.source()).isEqualTo("payment-service");
            assertThat(envelope.eventId()).isNotNull();
            assertThat(envelope.type()).startsWith("payment.");
        });
    }

    @Test
    @DisplayName("each record type gets its own subject, so a new event type cannot break an old schema")
    void schemasAreRegisteredPerRecordType() {
        publisher.publish(KafkaTopics.PAYMENT_EVENTS, UUID.randomUUID().toString(),
            new PaymentEvent.PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("4.99"), "EUR", "transfer", Instant.now())).join();

        List<String> subjects = RestClient.create().get()
            .uri(schemaRegistryUrl + "/subjects")
            .retrieve()
            .body(new ParameterizedTypeReference<List<String>>() {
            });

        List<String> paymentSubjects = subjects.stream()
            .filter(subject -> subject.startsWith(KafkaTopics.PAYMENT_EVENTS + "-"))
            .toList();
        // One per distinct event type published so far in this JVM: the region
        // is shared by every test class of the module.
        assertThat(paymentSubjects).hasSizeGreaterThanOrEqualTo(2);
        assertThat(paymentSubjects).allSatisfy(subject ->
            assertThat(subject).startsWith(KafkaTopics.PAYMENT_EVENTS + "-"));
    }

    /**
     * A raw consumer with the same serialisers the services use: the domain
     * interface is the declared type, and Jackson resolves the concrete event
     * from the payload's {@code eventType} discriminator.
     */
    private List<ConsumerRecord<String, PaymentEvent>> drain(String key, int expected) {
        Map<String, Object> props = new java.util.HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("json.value.type", PaymentEvent.class.getName());

        List<ConsumerRecord<String, PaymentEvent>> records = new ArrayList<>();
        try (KafkaConsumer<String, PaymentEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopics.PAYMENT_EVENTS));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                // Filter by key: the topic is shared by every test in the JVM.
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        records.add(record);
                    }
                });
            }
        }
        return records;
    }
}
