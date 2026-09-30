package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


import java.time.Instant;
import java.util.UUID;

/**
 * Events published by user-service on {@link KafkaTopics#USER_EVENTS}.
 *
 * <p>{@link UserRegistered} is what makes asynchronous flows possible without
 * synchronous lookups: it is emitted when the local account is provisioned, and
 * it is the only place where the email address is published. Consumers that
 * need to reach a user (notification-service, for one) keep a projection of it
 * instead of calling back into the identity service for every message.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserEvent.UserRegistered.class, name = "user-registered"),
    @JsonSubTypes.Type(value = UserEvent.RoleGranted.class, name = "role-granted"),
    @JsonSubTypes.Type(value = UserEvent.RoleRevoked.class, name = "role-revoked"),
    @JsonSubTypes.Type(value = UserEvent.RoleChangeFailed.class, name = "role-change-failed")
})
public sealed interface UserEvent
    permits UserEvent.UserRegistered, UserEvent.RoleGranted,
            UserEvent.RoleRevoked, UserEvent.RoleChangeFailed {

    UUID userId();

    Instant occurredAt();

    /**
     * The moment an account becomes known to the platform, with the contact
     * data downstream flows need.
     */
    record UserRegistered(
        UUID userId,
        String email,
        String displayName,
        Instant occurredAt
    ) implements UserEvent {
    }

    /**
     * The orchestrated saga's positive reply: the role is on the token now.
     * {@code sagaId} echoes the command so the orchestrator can match the reply
     * to the flow it is driving.
     */
    record RoleGranted(
        UUID sagaId,
        UUID userId,
        String role,
        Instant occurredAt
    ) implements UserEvent {
    }

    /** The orchestrated saga's compensation, confirmed. */
    record RoleRevoked(
        UUID sagaId,
        UUID userId,
        String role,
        String reason,
        Instant occurredAt
    ) implements UserEvent {
    }

    /** The identity provider refused; the orchestrator must compensate. */
    record RoleChangeFailed(
        UUID sagaId,
        UUID userId,
        String role,
        String reason,
        Instant occurredAt
    ) implements UserEvent {
    }
}
