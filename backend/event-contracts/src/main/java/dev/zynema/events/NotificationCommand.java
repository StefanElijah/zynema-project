package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


import java.util.Map;
import java.util.UUID;

/**
 * Commands sent to notification-service on
 * {@link KafkaTopics#NOTIFICATION_COMMANDS}.
 *
 * <p>The command names a template and the data it needs; the recipient is
 * resolved from the service's own contact projection, so a command never
 * carries an email address around.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NotificationCommand.SendNotification.class, name = "send-notification")
})
public sealed interface NotificationCommand
    permits NotificationCommand.SendNotification {

    /**
     * Template names are part of the contract: a producer must name one of
     * these, and the notification service decides how to render it.
     */
    String SUBSCRIPTION_WELCOME = "subscription-welcome";

    UUID userId();

    String template();

    record SendNotification(
        UUID notificationId,
        UUID userId,
        String template,
        Map<String, String> data
    ) implements NotificationCommand {
    }
}
