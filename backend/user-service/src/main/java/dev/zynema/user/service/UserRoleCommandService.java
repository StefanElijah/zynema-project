package dev.zynema.user.service;

import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserCommand;
import dev.zynema.events.UserEvent;
import dev.zynema.user.domain.User;
import dev.zynema.user.identity.KeycloakAdminClient;
import dev.zynema.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Executes the orchestrated saga's role commands (ADR-0007).
 *
 * <p>A command has exactly one executor: this service changes the IdP and
 * appends the reply event to the outbox in the same transaction. The reply
 * carries the command's {@code sagaId} back so the orchestrator can match it.
 *
 * <p>A command is not deduplicated: the Keycloak grant is naturally
 * idempotent, and the orchestrator ignores a reply that arrives for a step it
 * has already left. The failure path is different — a command that exhausts its
 * retries becomes {@code RoleChangeFailed} through the dead-letter handler, so
 * the saga can compensate instead of waiting forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleCommandService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloak;
    private final OutboxRecorder outbox;

    @Transactional
    public void onUserCommand(EventEnvelope<UserCommand> envelope) {
        switch (envelope.payload()) {
            case UserCommand.GrantRole grant -> grant(grant);
            case UserCommand.RevokeRole revoke -> revoke(revoke);
        }
    }

    private void grant(UserCommand.GrantRole grant) {
        User user = requireLinkedUser(grant.userId());
        keycloak.grantRealmRole(user.getKeycloakSubject(), grant.role());
        outbox.append(KafkaTopics.USER_EVENTS, user.getId().toString(),
            new UserEvent.RoleGranted(grant.sagaId(), user.getId(), grant.role(), Instant.now()));
    }

    private void revoke(UserCommand.RevokeRole revoke) {
        User user = requireLinkedUser(revoke.userId());
        keycloak.revokeRealmRole(user.getKeycloakSubject(), revoke.role());
        outbox.append(KafkaTopics.USER_EVENTS, user.getId().toString(),
            new UserEvent.RoleRevoked(revoke.sagaId(), user.getId(), revoke.role(),
                revoke.reason(), Instant.now()));
    }

    private User requireLinkedUser(java.util.UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getKeycloakSubject() == null || user.getKeycloakSubject().isBlank()) {
            throw new IllegalStateException("User " + userId + " is not linked to a Keycloak subject");
        }
        return user;
    }
}
