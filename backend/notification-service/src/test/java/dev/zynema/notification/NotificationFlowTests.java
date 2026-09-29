package dev.zynema.notification;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.events.UserEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestClient;

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
 * The happy path of the choreographed saga (ADR-0007): the registration
 * projects a contact, the subscription triggers the welcome email, and the
 * acknowledgement leaves through the outbox. MailHog is inspected through its
 * own API, like a developer would.
 */
class NotificationFlowTests extends AbstractNotificationIntegrationTest {

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
    @DisplayName("UserRegistered projects the contact and SubscriptionCreated delivers the welcome email")
    void registrationThenSubscriptionSendsTheEmail() {
        UUID userId = UUID.randomUUID();
        String email = "viewer-%s@zynema.dev".formatted(userId);
        UserEvent.UserRegistered registered = new UserEvent.UserRegistered(
            userId, email, "Viewer One", Instant.now());
        publisher.publish(KafkaTopics.USER_EVENTS, userId.toString(),
            EventMetadata.of("user-service", KafkaTopics.USER_EVENTS, registered, null), registered).join();

        await("the contact projection", () -> 1L == jdbc.queryForObject(
            "SELECT count(*) FROM contacts WHERE user_id = ?", Long.class, userId));

        UUID subscriptionId = UUID.randomUUID();
        PaymentEvent.SubscriptionCreated created = new PaymentEvent.SubscriptionCreated(
            subscriptionId, userId, UUID.randomUUID(), "standard",
            new BigDecimal("9.99"), "USD", Instant.now());
        publisher.publish(KafkaTopics.PAYMENT_EVENTS, subscriptionId.toString(),
            EventMetadata.of("payment-service", KafkaTopics.PAYMENT_EVENTS, created, null), created).join();

        await("the welcome email", () -> mailTotal() >= 1);

        Map<String, Object> content = contentOfFirstMessage();
        assertThat(mailHeader(content, "To")).contains(email);
        assertThat(mailHeader(content, "Subject")).containsExactly("Welcome to Zynema");
        assertThat((String) content.get("Body")).contains("Your standard subscription is active");

        await("the SENT log row", () -> 1L == jdbc.queryForObject(
            "SELECT count(*) FROM notification_log WHERE status = 'SENT' AND recipient = ?",
            Long.class, email));

        String notificationId = jdbc.queryForObject(
            "SELECT notification_id::text FROM notification_log", String.class);
        await("the acknowledgement in the outbox", () -> 1L == jdbc.queryForObject("""
            SELECT count(*) FROM outbox
            WHERE subject = ? AND type = 'notification.notification-sent'
            """, Long.class, notificationId));

        relay.publishPending();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class)).isZero();

        ConsumerRecord<String, NotificationEvent> record = drain(notificationId, 1).get(0);
        assertThat(record.value()).isInstanceOf(NotificationEvent.NotificationSent.class);
        assertThat(EventEnvelopes.of(record).subject()).isEqualTo(notificationId);
    }

    @Test
    @DisplayName("a redelivered SubscriptionCreated sends no second email")
    void redeliveryDoesNotDuplicateTheEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "viewer-%s@zynema.dev".formatted(userId);
        UserEvent.UserRegistered registered = new UserEvent.UserRegistered(
            userId, email, "Viewer Two", Instant.now());
        publisher.publish(KafkaTopics.USER_EVENTS, userId.toString(),
            EventMetadata.of("user-service", KafkaTopics.USER_EVENTS, registered, null), registered).join();
        await("the contact projection", () -> 1L == jdbc.queryForObject(
            "SELECT count(*) FROM contacts WHERE user_id = ?", Long.class, userId));

        UUID subscriptionId = UUID.randomUUID();
        PaymentEvent.SubscriptionCreated created = new PaymentEvent.SubscriptionCreated(
            subscriptionId, userId, UUID.randomUUID(), "basic",
            new BigDecimal("4.99"), "USD", Instant.now());
        // The same metadata twice is what an at-least-once redelivery looks like.
        EventMetadata metadata = EventMetadata.of("payment-service", KafkaTopics.PAYMENT_EVENTS, created, null);
        publisher.publish(KafkaTopics.PAYMENT_EVENTS, subscriptionId.toString(), metadata, created).join();
        await("the welcome email", () -> mailTotal() >= 1);

        publisher.publish(KafkaTopics.PAYMENT_EVENTS, subscriptionId.toString(), metadata, created).join();
        // No signal exists for "the duplicate was acknowledged as a no-op"; the
        // assertion is that nothing new appears within a generous window.
        Thread.sleep(1_500);

        assertThat(mailTotal()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM notification_log", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM processed_events WHERE handler = 'subscription-welcome-email'
            """, Long.class)).isEqualTo(1);
    }

    // ───────────────────────────── mail ───────────────────────────────

    /** MailHog answers {@code text/json}, which RestClient will not convert: parse it ourselves. */
    private Map<String, Object> mailMessages() {
        String body = RestClient.create().get()
            .uri(mailhogApiUrl() + "/api/v2/messages")
            .retrieve()
            .body(String.class);
        try {
            return new ObjectMapper().readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the MailHog response", ex);
        }
    }

    private long mailTotal() {
        return ((Number) mailMessages().get("total")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contentOfFirstMessage() {
        List<Map<String, Object>> items = (List<Map<String, Object>>) mailMessages().get("items");
        return (Map<String, Object>) items.get(0).get("Content");
    }

    @SuppressWarnings("unchecked")
    private List<String> mailHeader(Map<String, Object> content, String name) {
        Map<String, Object> headers = (Map<String, Object>) content.get("Headers");
        return (List<String>) headers.get(name);
    }

    // ───────────────────────────── kafka ──────────────────────────────

    private List<ConsumerRecord<String, NotificationEvent>> drain(String key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-flow-test-" + UUID.randomUUID());
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
