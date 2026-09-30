package dev.zynema.payment.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserEvent;
import dev.zynema.payment.saga.SubscriptionOnboardingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * The replies to the orchestrator's role command. Always active: without a
 * running saga it is a no-op, and it does not guess a saga from the user id —
 * the reply carries the saga id the command was sent with (ADR-0029).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final SubscriptionOnboardingOrchestrator orchestrator;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.USER_EVENTS, groupId = "payment-service",
        containerFactory = "userEventsListenerContainerFactory")
    public void onUserEvent(ConsumerRecord<String, UserEvent> record) {
        orchestrator.onUserEvent(EventEnvelopes.of(record));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        log.error("User reply with key {} exhausted its retries; "
            + "the saga it belongs to will stay in its current step", record.key());
    }
}
