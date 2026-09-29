package dev.zynema.notification.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationCommand;
import dev.zynema.notification.notification.WelcomeEmailService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * The single executor of the orchestrated saga's notification commands
 * (ADR-0007). Commands only arrive from the orchestrator; the choreographed
 * flow triggers the same work through {@code PaymentEventsListener}.
 */
@Component
@RequiredArgsConstructor
public class NotificationCommandListener {

    private final WelcomeEmailService welcomeEmail;
    private final DeadLetterService deadLetters;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.NOTIFICATION_COMMANDS, groupId = "notification-service",
        containerFactory = "notificationCommandsListenerContainerFactory")
    public void onNotificationCommand(ConsumerRecord<String, NotificationCommand> record) {
        welcomeEmail.onSendNotification(EventEnvelopes.of(record));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        deadLetters.persist(record, "notification-commands");
    }
}
