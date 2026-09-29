package dev.zynema.payment.saga;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.ProcessedEventStore;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationCommand;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.events.UserCommand;
import dev.zynema.events.UserEvent;
import dev.zynema.payment.domain.OnboardingSagaState;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.domain.SubscriptionOnboardingSaga;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.repository.SubscriptionOnboardingSagaRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The in-house orchestrator of the subscription onboarding (ADR-0007,
 * ADR-0029): an explicit state machine instead of emergent choreography.
 *
 * <p>It reacts to its own {@code SubscriptionCreated} — the flow starts at a
 * fact, not at an internal call — and then drives two steps with commands:
 * grant the subscriber role in the IdP, then send the welcome email. Every
 * transition reads the saga row and only acts if the step is the expected one,
 * so a redelivered reply cannot advance the flow twice; commands leave through
 * the same outbox as events, which keeps the state change and the message
 * atomic.
 *
 * <p>Compensation is one explicit path: if the role cannot be granted, the
 * subscription is canceled and {@code SubscriptionCancelled} records why. A
 * failed welcome email is deliberately not compensated — the platform decides
 * to keep the customer's access and let its own notification failure be the
 * flag (ADR-0028).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionOnboardingOrchestrator {

    public static final String TRIGGER_HANDLER = "subscription-onboarding-saga";
    public static final String SUBSCRIBER_ROLE = "subscriber";

    private static final int MAX_REASON = 500;

    private final SubscriptionOnboardingSagaRepository sagas;
    private final SubscriptionRepository subscriptions;
    private final ProcessedEventStore processedEvents;
    private final OutboxRecorder outbox;

    @Transactional
    public void start(EventEnvelope<PaymentEvent> envelope) {
        if (!(envelope.payload() instanceof PaymentEvent.SubscriptionCreated created)) {
            return;
        }
        if (!processedEvents.isNew(envelope, TRIGGER_HANDLER)) {
            return;
        }

        SubscriptionOnboardingSaga saga = new SubscriptionOnboardingSaga();
        saga.setId(UUID.randomUUID());
        saga.setSubscriptionId(created.subscriptionId());
        saga.setUserId(created.userId());
        saga.setPlanCode(created.planCode());
        saga.setState(OnboardingSagaState.AWAITING_ROLE);
        sagas.save(saga);

        outbox.append(KafkaTopics.USER_COMMANDS, created.userId().toString(),
            new UserCommand.GrantRole(saga.getId(), created.userId(), SUBSCRIBER_ROLE));
        log.info("Onboarding saga {} started for subscription {}", saga.getId(), created.subscriptionId());
    }

    @Transactional
    public void onUserEvent(EventEnvelope<UserEvent> envelope) {
        switch (envelope.payload()) {
            case UserEvent.RoleGranted granted -> onRoleGranted(granted);
            case UserEvent.RoleChangeFailed failed -> onRoleChangeFailed(failed);
            default -> log.debug("User event {} is not part of the onboarding flow",
                envelope.type());
        }
    }

    @Transactional
    public void onNotificationEvent(EventEnvelope<NotificationEvent> envelope) {
        switch (envelope.payload()) {
            case NotificationEvent.NotificationSent sent -> onNotificationSent(sent);
            case NotificationEvent.NotificationFailed failed -> onNotificationFailed(failed);
        }
    }

    // ───────────────────────────── steps ──────────────────────────────

    private void onRoleGranted(UserEvent.RoleGranted granted) {
        SubscriptionOnboardingSaga saga = sagas.findById(granted.sagaId()).orElse(null);
        if (saga == null || saga.getState() != OnboardingSagaState.AWAITING_ROLE) {
            log.debug("RoleGranted for saga {} ignored: the step is already done", granted.sagaId());
            return;
        }

        UUID notificationId = UUID.randomUUID();
        saga.setNotificationId(notificationId);
        saga.setState(OnboardingSagaState.AWAITING_NOTIFICATION);
        saga.setUpdatedAt(Instant.now());

        outbox.append(KafkaTopics.NOTIFICATION_COMMANDS, granted.userId().toString(),
            new NotificationCommand.SendNotification(notificationId, granted.userId(),
                NotificationCommand.SUBSCRIPTION_WELCOME,
                Map.of("planCode", saga.getPlanCode() == null ? "" : saga.getPlanCode())));
        log.info("Saga {}: role granted, welcome notification {} requested", saga.getId(), notificationId);
    }

    private void onRoleChangeFailed(UserEvent.RoleChangeFailed failed) {
        SubscriptionOnboardingSaga saga = sagas.findById(failed.sagaId()).orElse(null);
        if (saga == null || saga.getState() != OnboardingSagaState.AWAITING_ROLE) {
            return;
        }

        String reason = "role upgrade failed: " + failed.reason();
        Subscription subscription = subscriptions.findById(saga.getSubscriptionId()).orElse(null);
        if (subscription != null && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setCanceledAt(Instant.now());
            subscriptions.save(subscription);
            outbox.append(KafkaTopics.PAYMENT_EVENTS, subscription.getId().toString(),
                new PaymentEvent.SubscriptionCancelled(subscription.getId(), saga.getUserId(),
                    reason, Instant.now()));
        }

        saga.setState(OnboardingSagaState.COMPENSATED);
        saga.setFailureReason(truncate(reason));
        saga.setUpdatedAt(Instant.now());
        log.warn("Saga {} compensated: {}", saga.getId(), reason);
    }

    private void onNotificationSent(NotificationEvent.NotificationSent sent) {
        SubscriptionOnboardingSaga saga = sagas.findByNotificationId(sent.notificationId()).orElse(null);
        if (saga == null || saga.getState() != OnboardingSagaState.AWAITING_NOTIFICATION) {
            return;
        }
        saga.setState(OnboardingSagaState.COMPLETED);
        saga.setUpdatedAt(Instant.now());
        log.info("Saga {} completed for subscription {}", saga.getId(), saga.getSubscriptionId());
    }

    private void onNotificationFailed(NotificationEvent.NotificationFailed failed) {
        SubscriptionOnboardingSaga saga = sagas.findByNotificationId(failed.notificationId()).orElse(null);
        if (saga == null || saga.getState() != OnboardingSagaState.AWAITING_NOTIFICATION) {
            return;
        }
        // The platform's decision, same as the choreographed flow: keep the
        // access, flag the failure. The compensation service records and
        // exposes it independently of the saga.
        saga.setState(OnboardingSagaState.COMPLETED);
        saga.setFailureReason(truncate("notification failed: " + failed.reason()));
        saga.setUpdatedAt(Instant.now());
        log.warn("Saga {} completed with a failed notification: {}", saga.getId(), failed.reason());
    }

    private String truncate(String value) {
        return value.length() <= MAX_REASON ? value : value.substring(0, MAX_REASON);
    }
}
