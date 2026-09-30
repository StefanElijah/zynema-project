package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


import java.util.UUID;

/**
 * Commands sent to user-service on {@link KafkaTopics#USER_COMMANDS}.
 *
 * <p>A command, not an event: it says "do this" and has exactly one logical
 * executor. The orchestrated saga sends these and waits for the reply event,
 * which is what makes the flow observable in one place.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserCommand.GrantRole.class, name = "grant-role"),
    @JsonSubTypes.Type(value = UserCommand.RevokeRole.class, name = "revoke-role")
})
public sealed interface UserCommand
    permits UserCommand.GrantRole, UserCommand.RevokeRole {

    UUID userId();

    String role();

    /**
     * @param sagaId the orchestrated flow this command belongs to, echoed in
     *               the reply so the orchestrator can match them
     */
    record GrantRole(UUID sagaId, UUID userId, String role) implements UserCommand {
    }

    record RevokeRole(UUID sagaId, UUID userId, String role, String reason) implements UserCommand {
    }
}
