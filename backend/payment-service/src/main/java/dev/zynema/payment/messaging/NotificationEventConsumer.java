package dev.zynema.payment.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.payment.service.NotificationCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Listens for the notification side of the saga and hands it to the
 * compensation (ADR-0007). The domain interface is the declared value type, so
 * Jackson resolves the concrete event from its discriminator (ADR-0025).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationCompensationService compensation;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.NOTIFICATION_EVENTS, groupId = "payment-service",
        containerFactory = "notificationEventsListenerContainerFactory")
    public void onNotificationEvent(ConsumerRecord<String, NotificationEvent> record) {
        compensation.onNotificationFailed(EventEnvelopes.of(record));
    }

    /**
     * Retries exhausted: without a record of the failure the saga would vanish,
     * so the dead letter is loud. It is not persisted here — payment's job is
     * the subscription, and notification-service owns its delivery history.
     * The value type is {@code Object} on purpose: a retry topic that lost the
     * declared type must not make the handler itself explode.
     */
    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        log.error("Notification event with key {} exhausted its retries; "
            + "no subscription was flagged for it", record.key());
    }
}
