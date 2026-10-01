package dev.zynema.user.messaging;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserCommand;
import dev.zynema.events.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A role command that exhausted its retries must not leave the orchestrator
 * waiting: the reply {@code RoleChangeFailed} is emitted instead, and the saga
 * compensates. The payload may arrive typed or as a map, so the extraction is
 * defensive for the same reason notification's dead-letter handler is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandDeadLetterService {

    private static final String DLT_ERROR_HEADER = "kafka_dlt-exception-message";

    private final OutboxRecorder outbox;

    @Transactional
    public void persist(ConsumerRecord<String, Object> record) {
        String reason = header(record, DLT_ERROR_HEADER);
        if (reason == null) {
            reason = "Retries exhausted while applying the role command";
        }

        UUID sagaId = field(record.value(), "sagaId");
        UUID userId = field(record.value(), "userId");
        String role = text(record.value(), "role");

        if (sagaId == null || userId == null || role == null) {
            log.error("Role command with key {} exhausted its retries and cannot be answered: {}",
                record.key(), reason);
            return;
        }

        outbox.append(KafkaTopics.USER_EVENTS, userId.toString(),
            new UserEvent.RoleChangeFailed(sagaId, userId, role, reason, Instant.now()));
        log.error("Role command for saga {} failed permanently; RoleChangeFailed emitted: {}",
            sagaId, reason);
    }

    private UUID field(Object payload, String name) {
        Object value = raw(payload, name);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String text(Object payload, String name) {
        Object value = raw(payload, name);
        return value instanceof String text ? text : null;
    }

    private Object raw(Object payload, String name) {
        if (payload instanceof UserCommand.GrantRole grant) {
            return switch (name) {
                case "sagaId" -> grant.sagaId();
                case "userId" -> grant.userId();
                case "role" -> grant.role();
                default -> null;
            };
        }
        if (payload instanceof UserCommand.RevokeRole revoke) {
            return switch (name) {
                case "sagaId" -> revoke.sagaId();
                case "userId" -> revoke.userId();
                case "role" -> revoke.role();
                default -> null;
            };
        }
        if (payload instanceof Map<?, ?> map) {
            return map.get(name);
        }
        return null;
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
