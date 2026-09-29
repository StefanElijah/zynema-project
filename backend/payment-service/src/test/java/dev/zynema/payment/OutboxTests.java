package dev.zynema.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.OutboxRelay;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox, end to end and with real infrastructure (ADR-0008, ADR-0026):
 * events are appended inside the business transaction, the relay publishes
 * them, and a rollback leaves nothing behind.
 */
class OutboxTests extends AbstractPaymentIntegrationTest {

    private static final UUID BASIC_PLAN = UUID.fromString("81000000-0000-4000-8000-000000000001");

    @Autowired
    private dev.zynema.payment.service.SubscriptionService subscriptionService;

    @Autowired
    private OutboxRecorder outbox;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Test
    @DisplayName("subscribing records its events in the same transaction and the relay publishes them")
    void subscribeAppendsAndTheRelayPublishes() {
        UUID userId = UUID.randomUUID();

        var subscription = subscriptionService.createSubscription(userId, BASIC_PLAN, "card");

        assertThat(unpublishedFor(subscription.getId())).isEqualTo(2);

        relay.publishPending();

        assertThat(unpublishedFor(subscription.getId())).isZero();

        List<ConsumerRecord<String, PaymentEvent>> records =
            drain(subscription.getId().toString(), 2);

        assertThat(records).extracting(ConsumerRecord::value)
            .hasAtLeastOneElementOfType(PaymentEvent.SubscriptionCreated.class);
        assertThat(records).extracting(ConsumerRecord::value)
            .hasAtLeastOneElementOfType(PaymentEvent.PaymentSucceeded.class);
        assertThat(records).allSatisfy(record ->
            assertThat(EventEnvelopes.of(record).subject()).isEqualTo(subscription.getId().toString()));
    }

    @Test
    @DisplayName("a rollback takes the event with it: no row, nothing published")
    void rolledBackTransactionsLeaveNoEvent() {
        UUID eventId = transactions.execute(status -> {
            UUID id = outbox.append(KafkaTopics.PAYMENT_EVENTS, UUID.randomUUID().toString(),
                new PaymentEvent.PaymentSucceeded(UUID.randomUUID(), UUID.randomUUID(),
                    new BigDecimal("1.00"), "USD", "transfer", Instant.now()));
            status.setRollbackOnly();
            return id;
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE id = ?", Long.class, eventId))
            .isZero();
    }

    @Test
    @DisplayName("a republished event keeps its id: consumers can deduplicate it")
    void republishingKeepsTheEventId() {
        UUID userId = UUID.randomUUID();
        var subscription = subscriptionService.createSubscription(userId, BASIC_PLAN, "card");
        relay.publishPending();

        var first = drain(subscription.getId().toString(), 2).stream()
            .filter(record -> record.value() instanceof PaymentEvent.SubscriptionCreated)
            .findFirst().orElseThrow();

        // What a crash between publishing and marking would leave behind.
        jdbc.update("UPDATE outbox SET published_at = NULL WHERE id = ?",
            EventEnvelopes.of(first).eventId());
        relay.publishPending();

        var republished = drain(subscription.getId().toString(), 2).stream()
            .filter(record -> record.value() instanceof PaymentEvent.SubscriptionCreated)
            .findFirst().orElseThrow();

        assertThat(EventEnvelopes.of(republished).eventId())
            .isEqualTo(EventEnvelopes.of(first).eventId());
    }

    private long unpublishedFor(UUID subscriptionId) {
        return jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE subject = ? AND published_at IS NULL",
            Long.class, subscriptionId.toString());
    }

    private List<ConsumerRecord<String, PaymentEvent>> drain(String key, int expected) {
        Map<String, Object> props = new java.util.HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
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
