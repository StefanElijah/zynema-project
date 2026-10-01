package dev.zynema.user.messaging;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserCommand;
import dev.zynema.events.UserEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserCommandDeadLetterServiceTest {

    private static final String DLT_ERROR = "kafka_dlt-exception-message";

    private final OutboxRecorder outbox = mock(OutboxRecorder.class);
    private final UserCommandDeadLetterService service = new UserCommandDeadLetterService(outbox);

    private final UUID sagaId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void answersATypedGrantRoleWithRoleChangeFailed() {
        service.persist(record(new UserCommand.GrantRole(sagaId, userId, "subscriber"), "keycloak down"));

        UserEvent.RoleChangeFailed event = capture();
        assertThat(event.sagaId()).isEqualTo(sagaId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.role()).isEqualTo("subscriber");
        assertThat(event.reason()).isEqualTo("keycloak down");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void usesADefaultReasonWhenTheDeadLetterCarriesNone() {
        service.persist(record(new UserCommand.GrantRole(sagaId, userId, "subscriber"), null));

        assertThat(capture().reason())
            .isEqualTo("Retries exhausted while applying the role command");
    }

    @Test
    void readsARevokeRoleFromTheRetryTopics() {
        service.persist(record(new UserCommand.RevokeRole(sagaId, userId, "subscriber", "fraud"), null));

        assertThat(capture().role()).isEqualTo("subscriber");
    }

    @Test
    void readsACommandThatArrivedAsAMap() {
        service.persist(record(Map.of(
            "sagaId", sagaId.toString(),
            "userId", userId.toString(),
            "role", "subscriber"), null));

        assertThat(capture().userId()).isEqualTo(userId);
    }

    @Test
    void staysSilentWhenThePayloadCannotBeRead() {
        service.persist(record(Map.of("sagaId", sagaId.toString()), null));

        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void staysSilentWhenTheIdsAreNotUuids() {
        service.persist(record(Map.of(
            "sagaId", "not-a-uuid",
            "userId", userId.toString(),
            "role", "subscriber"), null));

        verify(outbox, never()).append(any(), any(), any());
    }

    private UserEvent.RoleChangeFailed capture() {
        ArgumentCaptor<UserEvent.RoleChangeFailed> event =
            ArgumentCaptor.forClass(UserEvent.RoleChangeFailed.class);
        verify(outbox).append(eq(KafkaTopics.USER_EVENTS), eq(userId.toString()), event.capture());
        return event.getValue();
    }

    private ConsumerRecord<String, Object> record(Object payload, String error) {
        ConsumerRecord<String, Object> record =
            new ConsumerRecord<>("zynema.user.commands-dlt", 0, 3L, "user-1", payload);
        if (error != null) {
            record.headers().add(DLT_ERROR, error.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
