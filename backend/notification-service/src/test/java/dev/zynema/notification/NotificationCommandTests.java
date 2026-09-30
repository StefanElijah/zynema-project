package dev.zynema.notification;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationCommand;
import dev.zynema.events.NotificationEvent;
import dev.zynema.notification.messaging.PaymentEventsListener;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.context.ApplicationContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The orchestrated side from notification's point of view (ADR-0029): the
 * email is a command from the saga, and the event trigger is off so the two
 * coordination styles cannot both fire.
 */
@TestPropertySource(properties = "zynema.saga.mode=orchestrated")
class NotificationCommandTests extends AbstractNotificationIntegrationTest {

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private OutboxRelay relay;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void reset() {
        resetNotificationState();
    }

    @Test
    @DisplayName("a SendNotification command sends the email and answers with NotificationSent")
    void commandSendsTheEmailAndReplies() {
        UUID userId = seedContact("command-viewer");
        UUID notificationId = UUID.randomUUID();
        NotificationCommand.SendNotification command = new NotificationCommand.SendNotification(
            notificationId, userId, "subscription-welcome", Map.of("planCode", "premium"));
        publisher.publish(KafkaTopics.NOTIFICATION_COMMANDS, notificationId.toString(),
            EventMetadata.of("payment-service", KafkaTopics.NOTIFICATION_COMMANDS, command, null), command).join();

        await("the welcome email", () -> mailTotal() >= 1);

        Map<String, Object> content = contentOfFirstMessage();
        assertThat(mailHeader(content, "To")).contains("command-viewer@zynema.dev");
        assertThat(mailHeader(content, "Subject")).containsExactly("Welcome to Zynema");
        assertThat((String) content.get("Body")).contains("Your premium subscription is active");

        await("the SENT log", () -> 1L == jdbc.queryForObject("""
            SELECT count(*) FROM notification_log WHERE notification_id = ? AND status = 'SENT'
            """, Long.class, notificationId));

        relay.publishPending();
        ConsumerRecord<String, NotificationEvent> record = drain(notificationId.toString(), 1).get(0);
        assertThat(record.value()).isInstanceOf(NotificationEvent.NotificationSent.class);
        assertThat(((NotificationEvent.NotificationSent) record.value()).notificationId())
            .isEqualTo(notificationId);
    }

    @Test
    @DisplayName("a redelivered command sends no second email")
    void commandRedeliveryIsIdempotent() throws Exception {
        UUID userId = seedContact("redelivery-viewer");
        UUID notificationId = UUID.randomUUID();
        NotificationCommand.SendNotification command = new NotificationCommand.SendNotification(
            notificationId, userId, "subscription-welcome", Map.of("planCode", "basic"));
        EventMetadata metadata = EventMetadata.of("payment-service",
            KafkaTopics.NOTIFICATION_COMMANDS, command, null);

        publisher.publish(KafkaTopics.NOTIFICATION_COMMANDS, notificationId.toString(), metadata, command).join();
        await("the welcome email", () -> mailTotal() >= 1);

        publisher.publish(KafkaTopics.NOTIFICATION_COMMANDS, notificationId.toString(), metadata, command).join();
        Thread.sleep(1_500);

        assertThat(mailTotal()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_log", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("in orchestrated mode the event-driven welcome listener does not exist: the command is the trigger")
    void subscriptionCreatedDoesNotTriggerInOrchestratedMode() {
        // Structural on purpose: publishing the event to prove nobody reacts
        // would leave it on the topic for the next context's consumer group,
        // which is how a test suite starts failing for the wrong reason.
        assertThat(applicationContext.getBeansOfType(PaymentEventsListener.class)).isEmpty();
    }

    private UUID seedContact(String recipient) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO contacts (user_id, email, display_name) VALUES (?, ?, ?)",
            userId, recipient + "@zynema.dev", "Command Viewer");
        return userId;
    }

    private List<ConsumerRecord<String, NotificationEvent>> drain(String key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-command-test-" + UUID.randomUUID());
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
