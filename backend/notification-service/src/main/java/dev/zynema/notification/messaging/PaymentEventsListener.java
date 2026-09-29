package dev.zynema.notification.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.PaymentEvent;
import dev.zynema.notification.notification.WelcomeEmailService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * The saga's trigger: a created subscription asks for the welcome email
 * (ADR-0007). Other payment events are acknowledged and ignored.
 */
@Component
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
