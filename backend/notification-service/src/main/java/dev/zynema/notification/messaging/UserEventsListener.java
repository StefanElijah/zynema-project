package dev.zynema.notification.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserEvent;
import dev.zynema.notification.contact.ContactProjectionService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/** Feeds the contact projection from the user domain (ADR-0025). */
@Component
@RequiredArgsConstructor
public class UserEventsListener {

    private final ContactProjectionService projection;
    private final DeadLetterService deadLetters;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.USER_EVENTS, groupId = "notification-service",
        containerFactory = "userEventsListenerContainerFactory")
    public void onUserEvent(ConsumerRecord<String, UserEvent> record) {
        projection.onUserRegistered(EventEnvelopes.of(record));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        deadLetters.persist(record, "user-events");
    }
}
