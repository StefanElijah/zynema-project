package dev.zynema.user.messaging;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserCommand;
import dev.zynema.user.service.UserRoleCommandService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/** The single executor of the orchestrated saga's role commands (ADR-0007). */
@Component
@RequiredArgsConstructor
public class UserCommandListener {

    private final UserRoleCommandService roleCommands;
    private final UserCommandDeadLetterService deadLetters;

    @RetryableTopic
    @KafkaListener(topics = KafkaTopics.USER_COMMANDS, groupId = "user-service",
        containerFactory = "userCommandsListenerContainerFactory")
    public void onUserCommand(ConsumerRecord<String, UserCommand> record) {
        roleCommands.onUserCommand(EventEnvelopes.of(record));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, Object> record) {
        deadLetters.persist(record);
    }
}
