package dev.zynema.payment.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.PaymentEvent;
import dev.zynema.payment.saga.SubscriptionOnboardingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Starts the orchestrated saga from payment's own published fact (ADR-0029).
 *
 * <p>Only present in {@code orchestrated} mode: in {@code choreographed} mode
 * nobody would consume this and the flow would simply not run, which is exactly
 * the switch the comparison needs.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "zynema.saga.mode", havingValue = "orchestrated")
@RequiredArgsConstructor
public class SubscriptionCreatedTriggerListener {

    private final SubscriptionOnboardingOrchestrator orchestrator;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "payment-service-onboarding",
        containerFactory = "paymentEventsListenerContainerFactory")
    public void onPaymentEvent(ConsumerRecord<String, PaymentEvent> record) {
        orchestrator.start(EventEnvelopes.of(record));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        log.error("SubscriptionCreated with key {} exhausted its retries; "
            + "the onboarding saga was never started", record.key());
    }
}
