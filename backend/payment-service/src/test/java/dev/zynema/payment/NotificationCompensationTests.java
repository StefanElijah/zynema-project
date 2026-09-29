package dev.zynema.payment;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.payment.domain.NotificationFailure;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.repository.IdempotencyKeyRepository;
import dev.zynema.payment.repository.NotificationFailureRepository;
import dev.zynema.payment.repository.PaymentRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import dev.zynema.payment.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The choreographed compensation (ADR-0007): a permanently failed welcome
 * email flags the subscription and is exposed, but money is never reversed.
 */
@AutoConfigureMockMvc
class NotificationCompensationTests extends AbstractPaymentIntegrationTest {

    private static final UUID BASIC_PLAN = UUID.fromString("81000000-0000-4000-8000-000000000001");
    private static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private SubscriptionService subscriptionService;

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

    @BeforeEach
    void reset() {
        failureRepository.deleteAll();
        paymentRepository.deleteAll();
        subscriptionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox");
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        USER_SERVICE.resetAll();
        USER_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "email": "demo@zynema.dev", "displayName": "Demo User"}
                    """.formatted(DEMO_USER))));
    }

    @Test
    @DisplayName("a permanently failed email flags the subscription and is exposed, without reversing money")
    void failureFlagsTheSubscriptionWithoutReversingIt() throws Exception {
        var subscription = subscriptionService.createSubscription(
            UUID.fromString(DEMO_USER), BASIC_PLAN, "card");
        relay.publishPending();

        NotificationEvent.NotificationFailed failed = new NotificationEvent.NotificationFailed(
            UUID.randomUUID(), subscription.getUserId(), "subscription-welcome",
            "mailbox refused the delivery", Instant.now());
        publisher.publish(KafkaTopics.NOTIFICATION_EVENTS, subscription.getId().toString(), failed).join();

        await("the failure row", () -> failureRepository.count() == 1);

        NotificationFailure row = failureRepository.findAll().get(0);
        assertThat(row.getNotificationId()).isEqualTo(failed.notificationId());
        assertThat(row.getSubscriptionId()).isEqualTo(subscription.getId());
        assertThat(row.getReason()).isEqualTo("mailbox refused the delivery");

        // The compensation is a flag, not an undo: access and money stay.
        assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getStatus())
            .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(paymentRepository.count()).isEqualTo(1);

        relay.publishPending();
        assertThat(jdbc.queryForObject(
            "SELECT type FROM outbox WHERE subject = ? AND type = 'payment.subscription-notification-failed'",
            String.class, subscription.getId().toString())).isEqualTo("payment.subscription-notification-failed");

        mockMvc.perform(get("/api/v1/payments/subscriptions/me").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andExpect(jsonPath("$.notificationFailure.template", is("subscription-welcome")))
            .andExpect(jsonPath("$.notificationFailure.reason", is("mailbox refused the delivery")));
    }

    @Test
    @DisplayName("a redelivered NotificationFailed is ignored: one row, one compensation event")
    void duplicateDeliveryIsIdempotent() {
        var subscription = subscriptionService.createSubscription(
            UUID.fromString(DEMO_USER), BASIC_PLAN, "card");
        relay.publishPending();

        NotificationEvent.NotificationFailed failed = new NotificationEvent.NotificationFailed(
            UUID.randomUUID(), subscription.getUserId(), "subscription-welcome", "smtp down", Instant.now());
        // Same event id twice: what an at-least-once redelivery looks like.
        EventMetadata metadata = EventMetadata.of("notification-service",
            KafkaTopics.NOTIFICATION_EVENTS, failed, null);
        publisher.publish(KafkaTopics.NOTIFICATION_EVENTS, subscription.getId().toString(), metadata, failed).join();
        publisher.publish(KafkaTopics.NOTIFICATION_EVENTS, subscription.getId().toString(), metadata, failed).join();

        await("the first delivery to be recorded", () -> failureRepository.count() == 1);
        await("the compensation event in the outbox", () -> 1 == jdbc.queryForObject("""
            SELECT count(*) FROM outbox
            WHERE subject = ? AND type = 'payment.subscription-notification-failed'
            """, Long.class, subscription.getId().toString()));

        assertThat(failureRepository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM outbox
            WHERE subject = ? AND type = 'payment.subscription-notification-failed'
            """, Long.class, subscription.getId().toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("a failure for an account without an active subscription is claimed and ignored")
    void failureWithoutSubscriptionIsIgnored() {
        NotificationEvent.NotificationFailed failed = new NotificationEvent.NotificationFailed(
            UUID.randomUUID(), UUID.randomUUID(), "subscription-welcome", "no account", Instant.now());

        publisher.publish(KafkaTopics.NOTIFICATION_EVENTS, UUID.randomUUID().toString(), failed).join();

        await("the claim", () -> jdbc.queryForObject(
            "SELECT count(*) FROM processed_events", Long.class) == 1);
        assertThat(failureRepository.count()).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM outbox WHERE type = 'payment.subscription-notification-failed'
            """, Long.class)).isZero();
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asDemo() {
        return jwt().jwt(jwt -> jwt.subject(DEMO_SUBJECT).claim("email", "demo@zynema.dev"))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }
}
