package dev.zynema.payment;

import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationCommand;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.UserCommand;
import dev.zynema.events.UserEvent;
import dev.zynema.payment.domain.OnboardingSagaState;
import dev.zynema.payment.domain.SubscriptionOnboardingSaga;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.repository.IdempotencyKeyRepository;
import dev.zynema.payment.repository.NotificationFailureRepository;
import dev.zynema.payment.repository.PaymentRepository;
import dev.zynema.payment.repository.SubscriptionOnboardingSagaRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import dev.zynema.payment.service.SubscriptionService;
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
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The orchestrated onboarding (ADR-0007, ADR-0029), driven by the explicit
 * state machine. The test plays the two services the saga commands and asserts
 * both the flow and its compensations, including the one that is deliberately
 * not an undo.
 */
@TestPropertySource(properties = "zynema.saga.mode=orchestrated")
class OrchestratedSagaTests extends AbstractPaymentIntegrationTest {

    private static final UUID BASIC_PLAN = UUID.fromString("81000000-0000-4000-8000-000000000001");

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionOnboardingSagaRepository sagaRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationFailureRepository failureRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @BeforeEach
    void reset() {
        failureRepository.deleteAll();
        sagaRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox");
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    @DisplayName("SubscriptionCreated starts the saga and the first command is GrantRole")
    void subscriptionCreatedStartsTheSaga() {
        UUID userId = UUID.randomUUID();
        subscriptionService.createSubscription(userId, BASIC_PLAN, "card");
        relay.publishPending();

        SubscriptionOnboardingSaga saga = awaitSaga(userId, OnboardingSagaState.AWAITING_ROLE);
        await("the GrantRole command in the outbox",
            () -> 1L == outboxCount("user.grant-role", userId));

        relay.publishPending();
        UserCommand command = drainCommands(KafkaTopics.USER_COMMANDS, UserCommand.class, userId.toString(), 1).get(0);
        assertThat(command).isInstanceOf(UserCommand.GrantRole.class);
        UserCommand.GrantRole grant = (UserCommand.GrantRole) command;
        assertThat(grant.sagaId()).isEqualTo(saga.getId());
        assertThat(grant.role()).isEqualTo("subscriber");
    }

    @Test
    @DisplayName("RoleGranted advances the saga and asks for the welcome notification exactly once")
    void roleGrantedAdvancesToTheNotificationCommand() {
        UUID userId = UUID.randomUUID();
        SubscriptionOnboardingSaga saga = startSaga(userId);

        // The same reply twice: the state machine must ignore the second.
        publishUserReply(new UserEvent.RoleGranted(saga.getId(), userId, "subscriber", Instant.now()));
        publishUserReply(new UserEvent.RoleGranted(saga.getId(), userId, "subscriber", Instant.now()));

        SubscriptionOnboardingSaga advanced = awaitSaga(userId, OnboardingSagaState.AWAITING_NOTIFICATION);
        assertThat(advanced.getNotificationId()).isNotNull();
        await("the SendNotification command in the outbox",
            () -> 1L == outboxCount("notification.send-notification", userId));

        relay.publishPending();
        NotificationCommand command = drainCommands(KafkaTopics.NOTIFICATION_COMMANDS,
            NotificationCommand.class, userId.toString(), 1).get(0);
        assertThat(command).isInstanceOf(NotificationCommand.SendNotification.class);
        NotificationCommand.SendNotification send = (NotificationCommand.SendNotification) command;
        assertThat(send.notificationId()).isEqualTo(advanced.getNotificationId());
        assertThat(send.template()).isEqualTo(NotificationCommand.SUBSCRIPTION_WELCOME);
        assertThat(send.data()).containsEntry("planCode", "basic");
    }

    @Test
    @DisplayName("NotificationSent closes the saga as completed")
    void notificationSentCompletesTheSaga() {
        UUID userId = UUID.randomUUID();
        SubscriptionOnboardingSaga saga = advanceToNotification(userId);

        NotificationEvent.NotificationSent sent = new NotificationEvent.NotificationSent(
            saga.getNotificationId(), userId, NotificationCommand.SUBSCRIPTION_WELCOME,
            "viewer@zynema.dev", Instant.now());
        publisher.publish(KafkaTopics.NOTIFICATION_EVENTS, saga.getNotificationId().toString(),
            EventMetadata.of("test", KafkaTopics.NOTIFICATION_EVENTS, sent, null), sent).join();

        SubscriptionOnboardingSaga completed = awaitSaga(userId, OnboardingSagaState.COMPLETED);
        assertThat(completed.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("RoleChangeFailed compensates: the subscription is canceled and the reason is published")
    void roleChangeFailedCompensates() {
        UUID userId = UUID.randomUUID();
        SubscriptionOnboardingSaga saga = startSaga(userId);

        UserEvent.RoleChangeFailed failed = new UserEvent.RoleChangeFailed(
            saga.getId(), userId, "subscriber", "role does not exist", Instant.now());
        publisher.publish(KafkaTopics.USER_EVENTS, userId.toString(),
            EventMetadata.of("test", KafkaTopics.USER_EVENTS, failed, null), failed).join();

        SubscriptionOnboardingSaga compensated = awaitSaga(userId, OnboardingSagaState.COMPENSATED);
        assertThat(compensated.getFailureReason()).contains("role upgrade failed");

        assertThat(subscriptionRepository.findById(saga.getSubscriptionId()).orElseThrow().getStatus())
            .isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(jdbc.queryForObject("""
            SELECT payload FROM outbox
            WHERE type = 'payment.subscription-cancelled' AND subject = ?
            """, String.class, saga.getSubscriptionId().toString())).contains("role upgrade failed");
    }

    @Test
    @DisplayName("a failed welcome email completes the saga with a warning and does not reverse anything")
    void notificationFailedCompletesWithAWarning() {
        UUID userId = UUID.randomUUID();
        SubscriptionOnboardingSaga saga = advanceToNotification(userId);

        NotificationEvent.NotificationFailed failed = new NotificationEvent.NotificationFailed(
            saga.getNotificationId(), userId, NotificationCommand.SUBSCRIPTION_WELCOME,
            "mailbox refused", Instant.now());
        publisher.publish(KafkaTopics.NOTIFICATION_EVENTS, saga.getNotificationId().toString(),
            EventMetadata.of("test", KafkaTopics.NOTIFICATION_EVENTS, failed, null), failed).join();

        SubscriptionOnboardingSaga completed = awaitSaga(userId, OnboardingSagaState.COMPLETED);
        assertThat(completed.getFailureReason()).contains("notification failed");

        // The independent compensation reader recorded the failure; the
        // subscription keeps its status and its money.
        await("the recorded notification failure", () -> 1L == jdbc.queryForObject("""
            SELECT count(*) FROM subscription_notification_failures WHERE notification_id = ?
            """, Long.class, saga.getNotificationId()));
        assertThat(subscriptionRepository.findById(saga.getSubscriptionId()).orElseThrow().getStatus())
            .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // ───────────────────────────── helpers ────────────────────────────

    /**
     * The orchestrated context consumes SubscriptionCreated events left by the
     * other contexts, so a saga is never identified by "the latest one": every
     * assertion is scoped to this test's user.
     */
    private SubscriptionOnboardingSaga awaitSaga(UUID userId, OnboardingSagaState state) {
        await("the saga of user " + userId + " to reach " + state, () -> sagaRepository.findAll().stream()
            .anyMatch(saga -> saga.getUserId().equals(userId) && saga.getState() == state));
        return sagaRepository.findAll().stream()
            .filter(saga -> saga.getUserId().equals(userId) && saga.getState() == state)
            .findFirst()
            .orElseThrow();
    }

    /** Creates the subscription, publishes its events and waits for the saga. */
    private SubscriptionOnboardingSaga startSaga(UUID userId) {
        subscriptionService.createSubscription(userId, BASIC_PLAN, "card");
        relay.publishPending();
        return awaitSaga(userId, OnboardingSagaState.AWAITING_ROLE);
    }

    /** Plays user-service: the role grant succeeded. */
    private SubscriptionOnboardingSaga advanceToNotification(UUID userId) {
        SubscriptionOnboardingSaga saga = startSaga(userId);
        publishUserReply(new UserEvent.RoleGranted(saga.getId(), userId, "subscriber", Instant.now()));
        return awaitSaga(userId, OnboardingSagaState.AWAITING_NOTIFICATION);
    }

    private void publishUserReply(UserEvent reply) {
        publisher.publish(KafkaTopics.USER_EVENTS, reply.userId().toString(),
            EventMetadata.of("test", KafkaTopics.USER_EVENTS, reply, null), reply).join();
    }

    private long outboxCount(String type, UUID userId) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM outbox WHERE type = ? AND subject = ?
            """, Long.class, type, userId.toString());
    }

    private <T> List<T> drainCommands(String topic, Class<T> declaredType, String key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "orchestrated-saga-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("json.value.type", declaredType.getName());

        List<T> records = new ArrayList<>();
        try (KafkaConsumer<String, T> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        records.add(record.value());
                    }
                });
            }
        }
        return records;
    }

    private void await(String description, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + description, ex);
            }
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
