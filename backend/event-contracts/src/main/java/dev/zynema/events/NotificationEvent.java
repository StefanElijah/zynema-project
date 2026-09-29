package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


import java.time.Instant;
import java.util.UUID;

/**
 * Events published by notification-service on
 * {@link KafkaTopics#NOTIFICATION_EVENTS}.
 *
 * <p>They close the loop of both sagas: a delivery that succeeded is an
 * acknowledgement, and one that failed permanently is what triggers the
 * compensation on the other side.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NotificationEvent.NotificationSent.class, name = "notification-sent"),
    @JsonSubTypes.Type(value = NotificationEvent.NotificationFailed.class, name = "notification-failed")
})
public sealed interface NotificationEvent
    permits NotificationEvent.NotificationSent, NotificationEvent.NotificationFailed {

    UUID notificationId();

    UUID userId();

    String template();

    Instant occurredAt();

    record NotificationSent(
        UUID notificationId,
        UUID userId,
        String template,
        String recipient,
        Instant occurredAt
    ) implements NotificationEvent {
    }

    record NotificationFailed(
        UUID notificationId,
        UUID userId,
        String template,
        String reason,
        Instant occurredAt
    ) implements NotificationEvent {
    }
}
