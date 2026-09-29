package dev.zynema.payment.service;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.ProcessedEventStore;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.payment.domain.NotificationFailure;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.domain.SubscriptionStatus;
import dev.zynema.payment.repository.NotificationFailureRepository;
import dev.zynema.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The compensation half of the choreographed saga (ADR-0007).
 *
 * <p>notification-service gives up on a delivery and publishes
 * {@code NotificationFailed}; payment is the only service that can decide what
 * that means for the subscription. The decision here is deliberately small:
 * <strong>record and expose, never reverse</strong>. The charge happened, the
 * customer has access, and a mailbox problem must not take either away.
 *
 * <p>Idempotency is layered: the claim in {@code processed_events} stops a
 * redelivery from doing the work twice, and the failure table's primary key is
 * the notification id, so even a lost claim cannot duplicate the row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCompensationService {

    public static final String HANDLER = "subscription-notification-failed";

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationFailureRepository failureRepository;
    private final ProcessedEventStore processedEvents;
    private final OutboxRecorder outbox;

    @Transactional
    public void onNotificationFailed(EventEnvelope<NotificationEvent> envelope) {
        if (!(envelope.payload() instanceof NotificationEvent.NotificationFailed failed)) {
            return;
        }
        if (!processedEvents.isNew(envelope, HANDLER)) {
            log.debug("Notification failure {} was already compensated", failed.notificationId());
            return;
        }

        subscriptionRepository.findWithPlanByUserIdAndStatus(failed.userId(), SubscriptionStatus.ACTIVE)
            .ifPresentOrElse(
                subscription -> record(subscription, failed),
                () -> log.warn("Notification failure {} for user {} has no active subscription to flag",
                    failed.notificationId(), failed.userId()));
    }

    private void record(Subscription subscription, NotificationEvent.NotificationFailed failed) {
        NotificationFailure failure = new NotificationFailure();
        failure.setNotificationId(failed.notificationId());
        failure.setSubscriptionId(subscription.getId());
        failure.setUserId(failed.userId());
        failure.setTemplate(failed.template());
        failure.setReason(failed.reason());
        failure.setOccurredAt(failed.occurredAt());
        failureRepository.save(failure);

        // The platform's record of the accepted compensation. It leaves through
        // the same outbox the subscription was born in (ADR-0026).
        outbox.append(KafkaTopics.PAYMENT_EVENTS, subscription.getId().toString(),
            new PaymentEvent.SubscriptionNotificationFailed(subscription.getId(), failed.userId(),
                failed.template(), failed.reason(), failed.occurredAt()));

        log.warn("Subscription {} keeps its money and its access, flagged with notification failure {} ({})",
            subscription.getId(), failed.notificationId(), failed.reason());
    }
}
