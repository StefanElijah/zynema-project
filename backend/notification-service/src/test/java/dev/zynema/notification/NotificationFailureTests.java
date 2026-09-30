package dev.zynema.notification;

import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The failure path of the choreographed saga (ADR-0007): retries are exhausted,
 * the record is persisted as a dead letter, and {@code NotificationFailed} goes
 * out so payment can compensate.
 */
class NotificationFailureTests extends AbstractNotificationIntegrationTest {

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private OutboxRelay relay;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @BeforeEach
    void reset() {
        resetNotificationState();
    }

    @Test
    @DisplayName("a welcome email that cannot be delivered is dead-lettered and triggers the compensation")
    void undeliverableWelcomeCompensates() {
        // No UserRegistered was ever consumed, so the contact cannot be found:
        // the retries are exhausted and the saga has to be told.
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PaymentEvent.SubscriptionCreated created = new PaymentEvent.SubscriptionCreated(
            subscriptionId, userId, UUID.randomUUID(), "basic",
            new BigDecimal("4.99"), "USD", Instant.now());
        EventMetadata metadata = EventMetadata.of("payment-service", KafkaTopics.PAYMENT_EVENTS, created, null);
        publisher.publish(KafkaTopics.PAYMENT_EVENTS, subscriptionId.toString(), metadata, created).join();

        await("the persisted dead letter", () -> 1L == jdbc.queryForObject("""
            SELECT count(*) FROM notification_dead_letters
            WHERE event_type = 'payment.subscription-created' AND event_id = ?
            """, Long.class, metadata.eventId()));

        // Filtered by this test's notification id: the topic is shared, and a
        // leftover from another class must not turn a correct flow red.
        await("the compensation in the outbox", () -> 1L == jdbc.queryForObject("""
            SELECT count(*) FROM outbox
            WHERE type = 'notification.notification-failed' AND subject = ?
            """, Long.class, metadata.eventId().toString()));
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM notification_log WHERE status = 'FAILED' AND notification_id = ?
            """, Long.class, metadata.eventId())).isEqualTo(1);

        relay.publishPending();
        ConsumerRecord<String, NotificationEvent> record = drain(metadata.eventId().toString(), 1).get(0);
        assertThat(record.value()).isInstanceOf(NotificationEvent.NotificationFailed.class);

        NotificationEvent.NotificationFailed failed = (NotificationEvent.NotificationFailed) record.value();
        assertThat(failed.notificationId()).isEqualTo(metadata.eventId());
        assertThat(failed.userId()).isEqualTo(userId);
        assertThat(failed.template()).isEqualTo("subscription-welcome");
    }

    private List<ConsumerRecord<String, NotificationEvent>> drain(String key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-failure-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("json.value.type", NotificationEvent.class.getName());

        List<ConsumerRecord<String, NotificationEvent>> records = new ArrayList<>();
        try (KafkaConsumer<String, NotificationEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopics.NOTIFICATION_EVENTS));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
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
