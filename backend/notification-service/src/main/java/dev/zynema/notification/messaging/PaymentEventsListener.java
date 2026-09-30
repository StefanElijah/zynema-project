package dev.zynema.notification.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.PaymentEvent;
import dev.zynema.notification.notification.WelcomeEmailService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * The choreographed saga's trigger: a created subscription asks for the welcome
 * email (ADR-0007). Other payment events are acknowledged and ignored.
 *
 * <p>It only exists in {@code choreographed} mode: in {@code orchestrated} mode
 * the email is a command from the orchestrator, and reacting to the event as
 * well would send two emails. The switch is the only difference between the two
 * styles from this service's point of view (ADR-0029).
 */
@Component
@ConditionalOnProperty(name = "zynema.saga.mode", havingValue = "choreographed", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentEventsListener {

    private final WelcomeEmailService welcomeEmail;
    private final DeadLetterService deadLetters;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "notification-service",
        containerFactory = "paymentEventsListenerContainerFactory")
    public void onPaymentEvent(ConsumerRecord<String, PaymentEvent> record) {
        welcomeEmail.onSubscriptionCreated(EventEnvelopes.of(record));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        deadLetters.persist(record, "payment-events");
    }
}
